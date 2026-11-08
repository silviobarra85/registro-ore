package it.registroore;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/** Verifica che i tasti +/- non si trasformino in testo ellittico (...). */
public class QuickHourButtonTest {
  public static void main(String[] args) throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      JButton minus=App.quickHourButton(false), plus=App.quickHourButton(true);
      for(JButton b:new JButton[]{minus,plus}) {
        if(b.getIcon()==null || b.getIcon().getIconWidth()<16) throw new AssertionError("Icona mancante");
        if(!b.getText().isEmpty()) throw new AssertionError("Il tasto non deve dipendere dal testo");
        if(b.getPreferredSize().width<40) throw new AssertionError("Pulsante troppo stretto");
        BufferedImage img=new BufferedImage(50,40,BufferedImage.TYPE_INT_ARGB);
        b.setSize(b.getPreferredSize());
        Graphics2D g=img.createGraphics();b.paint(g);g.dispose();
      }
      if(!minus.getAccessibleContext().getAccessibleName().contains("Sottrai")) throw new AssertionError("Accessibilita' meno");
      if(!plus.getAccessibleContext().getAccessibleName().contains("Aggiungi")) throw new AssertionError("Accessibilita' piu");
      System.out.println("OK: icone +/- vettoriali e accessibili");
    });
  }
}
