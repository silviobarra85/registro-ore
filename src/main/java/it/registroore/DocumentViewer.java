package it.registroore;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.zip.*;
import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
import org.xml.sax.InputSource;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

/** Visualizzatore documenti integrato: non esporta l'allegato se non su richiesta esplicita. */
final class DocumentViewer {
  private DocumentViewer(){}
  static void open(JFrame owner,String filename,String note,byte[] data) throws Exception {
    if(data==null)throw new IOException("Allegato senza contenuto.");
    JDialog dialog=new JDialog(owner,"Visualizza documento — "+filename,Dialog.ModalityType.MODELESS);
    dialog.setSize(980,740);dialog.setMinimumSize(new Dimension(600,430));dialog.setLocationRelativeTo(owner);
    dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    JPanel main=new JPanel(new BorderLayout(8,8));main.setBorder(new EmptyBorder(12,14,12,14));
    JPanel header=new JPanel(new BorderLayout(10,4));
    JLabel fileLabel=new JLabel(filename);fileLabel.setFont(fileLabel.getFont().deriveFont(Font.BOLD,16f));
    header.add(fileLabel,BorderLayout.WEST);
    header.add(new JLabel(String.format(Locale.ITALY,"%.1f KB",data.length/1024.0)),BorderLayout.EAST);
    main.add(header,BorderLayout.NORTH);
    JPanel footer=new JPanel(new BorderLayout(8,8));
    JLabel desc=new JLabel("Nota: "+((note==null||note.isBlank())?"nessuna":note));
    desc.setToolTipText(note);footer.add(desc,BorderLayout.CENTER);
    JButton external=new JButton("Apri con applicazione esterna");
    external.addActionListener(e->{try{openExternal(filename,data);}catch(Exception ex){JOptionPane.showMessageDialog(dialog,ex.getMessage(),"Errore apertura",JOptionPane.ERROR_MESSAGE);}});
    JButton close=new JButton("Chiudi");close.addActionListener(e->dialog.dispose());
    JPanel right=new JPanel(new FlowLayout(FlowLayout.RIGHT,7,0));right.add(external);right.add(close);
    footer.add(right,BorderLayout.EAST);main.add(footer,BorderLayout.SOUTH);
    String lower=filename.toLowerCase(Locale.ROOT);
    if(lower.endsWith(".pdf")){
      pdfPreview(dialog,main,data);
    }else if(List.of(".png",".jpg",".jpeg",".gif",".bmp",".webp").stream().anyMatch(lower::endsWith)){
      BufferedImage image=ImageIO.read(new ByteArrayInputStream(data));
      if(image==null)throw new IOException("Formato immagine non riconosciuto.");
      JLabel pic=new JLabel(new ImageIcon(image));pic.setHorizontalAlignment(SwingConstants.CENTER);
      main.add(new JScrollPane(pic),BorderLayout.CENTER);
    }else if(List.of(".txt",".csv",".tsv",".log",".md",".json",".xml",".java",".html",".sql",".ics").stream().anyMatch(lower::endsWith)){
      addText(main,new String(data,StandardCharsets.UTF_8));
    }else if(lower.endsWith(".docx")||lower.endsWith(".pptx")||lower.endsWith(".xlsx")||lower.endsWith(".odt")){
      String text=officeText(data,lower);
      addText(main,"ANTEPRIMA TESTUALE (non riproduce l'impaginazione originale)\n\n"+text);
    }else{
      JTextArea info=new JTextArea("Questo tipo di file non ha un visualizzatore interno disponibile.\n\n"+
        "Nome: "+filename+"\nDimensione: "+data.length+" byte\n\n"+
        "Per aprirlo senza modificare il database, scegli 'Apri con applicazione esterna'.");
      info.setEditable(false);info.setLineWrap(true);info.setWrapStyleWord(true);
      info.setBorder(new EmptyBorder(25,25,25,25));main.add(new JScrollPane(info),BorderLayout.CENTER);
    }
    dialog.setContentPane(main);dialog.setVisible(true);
  }
  private static void addText(JPanel parent,String text){
    JTextArea area=new JTextArea(text);area.setEditable(false);area.setFont(new Font(Font.MONOSPACED,Font.PLAIN,13));area.setLineWrap(true);area.setWrapStyleWord(true);
    area.setCaretPosition(0);area.setMargin(new Insets(14,14,14,14));
    parent.add(new JScrollPane(area),BorderLayout.CENTER);
  }
  private static void pdfPreview(JDialog dialog,JPanel main,byte[] data) throws IOException {
    PDDocument pdf=PDDocument.load(new ByteArrayInputStream(data));
    if(pdf.isEncrypted()){
      try{pdf.decrypt("");}catch(Exception e){pdf.close();throw new IOException("Il PDF richiede una password: aprilo con un programma esterno.",e);}
    }
    int pages=pdf.getNumberOfPages();
    if(pages==0){pdf.close();throw new IOException("Il PDF non contiene pagine.");}
    JPanel center=new JPanel(new BorderLayout(0,8));
    JPanel toolbar=new JPanel(new FlowLayout(FlowLayout.LEFT,9,3));
    JButton prev=new JButton("◀ Precedente"),next=new JButton("Successiva ▶");
    JLabel index=new JLabel();toolbar.add(prev);toolbar.add(index);toolbar.add(next);
    JLabel image=new JLabel("Caricamento anteprima PDF…",SwingConstants.CENTER);image.setVerticalAlignment(SwingConstants.TOP);
    JScrollPane scroller=new JScrollPane(image);scroller.getVerticalScrollBar().setUnitIncrement(20);
    center.add(toolbar,BorderLayout.NORTH);center.add(scroller,BorderLayout.CENTER);main.add(center,BorderLayout.CENTER);
    final int[] current={0};final SwingWorker<?,?>[] worker={null};
    Runnable redraw=()->{
      int n=current[0];index.setText("Pagina "+(n+1)+" di "+pages);
      prev.setEnabled(false);next.setEnabled(false);
      image.setIcon(null);image.setText("Rendering della pagina "+(n+1)+"…");
      SwingWorker<BufferedImage,Void> task=new SwingWorker<>(){
        protected BufferedImage doInBackground() throws Exception{
          PDPage page=(PDPage)pdf.getDocumentCatalog().getAllPages().get(n);
          return page.convertToImage(BufferedImage.TYPE_INT_RGB,105);
        }
        protected void done(){if(dialog.isDisplayable()&&!isCancelled())try{
          BufferedImage bi=get();image.setIcon(new ImageIcon(bi));image.setText("");
          SwingUtilities.invokeLater(()->scroller.getVerticalScrollBar().setValue(0));
        }catch(Exception ex){image.setText("Impossibile visualizzare la pagina: "+ex.getMessage());}
          prev.setEnabled(n>0);next.setEnabled(n<pages-1);
        }
      };
      worker[0]=task;task.execute();
    };
    prev.addActionListener(e->{if(current[0]>0){current[0]--;redraw.run();}});
    next.addActionListener(e->{if(current[0]+1<pages){current[0]++;redraw.run();}});
    dialog.addWindowListener(new java.awt.event.WindowAdapter(){public void windowClosed(java.awt.event.WindowEvent e){
      if(worker[0]!=null)worker[0].cancel(true);try{pdf.close();}catch(IOException ignored){}
    }});
    redraw.run();
  }
  private static void openExternal(String originalName,byte[] data) throws Exception {
    if(!Desktop.isDesktopSupported()||!Desktop.getDesktop().isSupported(Desktop.Action.OPEN))
      throw new IOException("L'apertura con app esterna non e disponibile su questo computer.");
    String safe=Paths.get(originalName.replace('\\','/')).getFileName().toString().replaceAll("[^a-zA-Z0-9._-]","_");
    if(safe.length()<4)safe="documento.dat";
    Path dest=Files.createTempFile("registro-ore-anteprima-","-"+safe);
    Files.write(dest,data);dest.toFile().deleteOnExit();
    Desktop.getDesktop().open(dest.toFile());
  }
  private static String officeText(byte[] data,String lower) throws Exception {
    Map<String,byte[]> xmls=new LinkedHashMap<>();
    try(ZipInputStream zis=new ZipInputStream(new ByteArrayInputStream(data))){
      ZipEntry entry;int count=0;
      while((entry=zis.getNextEntry())!=null){
        String name=entry.getName();boolean useful=(lower.endsWith(".docx")&&name.equals("word/document.xml"))||
          (lower.endsWith(".pptx")&&name.matches("ppt/slides/slide[0-9]+\\.xml"))||
          (lower.endsWith(".xlsx")&&(name.equals("xl/sharedStrings.xml")||name.matches("xl/worksheets/sheet[0-9]+\\.xml")))||
          (lower.endsWith(".odt")&&name.equals("content.xml"));
        if(useful){
          ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] chunk=new byte[8192];int n;
          while((n=zis.read(chunk))!=-1){
            if(out.size()+n>20_000_000)throw new IOException("Anteprima Office troppo grande.");
            out.write(chunk,0,n);
          }
          xmls.put(name,out.toByteArray());
        }
        if(++count>5000)throw new IOException("Documento Office con troppe parti.");
      }
    }
    if(xmls.isEmpty())return "Nessun testo leggibile trovato nel documento.";
    List<String> shared=new ArrayList<>();
    if(xmls.containsKey("xl/sharedStrings.xml")){
      Document doc=parseXml(xmls.get("xl/sharedStrings.xml"));NodeList strings=doc.getElementsByTagNameNS("*","si");
      for(int i=0;i<strings.getLength();i++)shared.add(textOf(strings.item(i),"t"));
    }
    StringBuilder output=new StringBuilder();
    for(var part:xmls.entrySet()){
      if(part.getKey().endsWith("sharedStrings.xml"))continue;
      output.append("\n--- ").append(part.getKey()).append(" ---\n");
      Document doc=parseXml(part.getValue());
      if(lower.endsWith(".xlsx")){
        NodeList rows=doc.getElementsByTagNameNS("*","row");
        for(int i=0;i<rows.getLength();i++){
          NodeList cells=((Element)rows.item(i)).getElementsByTagNameNS("*","c");
          for(int j=0;j<cells.getLength();j++){
            Element cell=(Element)cells.item(j);String val=textOf(cell,"v");
            if("s".equals(cell.getAttribute("t")))try{val=shared.get(Integer.parseInt(val));}catch(Exception ignored){}
            if(val.isEmpty())val=textOf(cell,"t");
            output.append(cell.getAttribute("r")).append(": ").append(val).append("    ");
          }
          output.append('\n');
          if(output.length()>350_000)break;
        }
      }else{
        String paragraphTag=lower.endsWith(".odt")?"p":"p";
        NodeList paragraphs=doc.getElementsByTagNameNS("*",paragraphTag);
        for(int i=0;i<paragraphs.getLength();i++){
          String text=textOf(paragraphs.item(i),lower.endsWith(".odt")?"span":"t");
          if(text.isBlank()&&lower.endsWith(".odt"))text=paragraphs.item(i).getTextContent();
          if(!text.isBlank())output.append(text).append('\n');
          if(output.length()>350_000)break;
        }
      }
      if(output.length()>350_000){output.append("\n... anteprima troncata ...");break;}
    }
    return output.toString();
  }
  private static Document parseXml(byte[] data) throws Exception {
    DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();f.setNamespaceAware(true);
    f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
    f.setFeature("http://xml.org/sax/features/external-general-entities",false);
    f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
    f.setXIncludeAware(false);f.setExpandEntityReferences(false);
    return f.newDocumentBuilder().parse(new InputSource(new ByteArrayInputStream(data)));
  }
  private static String textOf(Node parent,String local){
    if(!(parent instanceof Element))return "";
    NodeList nodes=((Element)parent).getElementsByTagNameNS("*",local);
    StringBuilder s=new StringBuilder();for(int i=0;i<nodes.getLength();i++)s.append(nodes.item(i).getTextContent());return s.toString();
  }
}
