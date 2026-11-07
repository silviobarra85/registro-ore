package it.registroore;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;

/** Font-independent vector symbols for the compact and expanded sidebar. */
final class NavigationIcons {
  private NavigationIcons() { }

  static Icon section(String group, Color color) { return new Symbol(kind(group), color, 24, 24, false); }
  static Icon header(String group, boolean open, Color color) { return new HeaderSymbol(kind(group), open, color); }
  static Icon sidebar(Color color) { return new Symbol("sidebar", color, 22, 22, false); }
  static Icon database(Color color) { return new Symbol("database", color, 24, 24, false); }

  private static String kind(String group) {
    return switch (group) {
      case "CALENDARIO E REGISTRO" -> "calendar";
      case "RENDICONTAZIONE" -> "chart";
      case "GESTIONE" -> "sliders";
      case "SCHEDE PROGETTO" -> "folder";
      case "SISTEMA" -> "gear";
      default -> "folder";
    };
  }

  private static final class HeaderSymbol implements Icon {
    private final String kind;
    private final boolean open;
    private final Color color;
    HeaderSymbol(String kind, boolean open, Color color) {this.kind=kind;this.open=open;this.color=color;}
    @Override public int getIconWidth(){return 40;}
    @Override public int getIconHeight(){return 22;}
    @Override public void paintIcon(Component c,Graphics g,int x,int y){
      Graphics2D a=(Graphics2D)g.create();
      try {
        a.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        a.setStroke(new BasicStroke(1.75f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        a.setColor(color);
        a.translate(x,y);
        Path2D.Double arrow=new Path2D.Double();
        if(open){arrow.moveTo(1,8);arrow.lineTo(5,12);arrow.lineTo(9,8);}
        else {arrow.moveTo(3,6);arrow.lineTo(7,11);arrow.lineTo(3,16);}
        a.draw(arrow);
        a.translate(15,1);
        a.scale(0.86,0.86);
        paintSymbol(a,kind);
      } finally {a.dispose();}
    }
  }

  private static final class Symbol implements Icon {
    private final String kind;
    private final Color color;
    private final int width,height;
    private final boolean disabled;
    Symbol(String kind,Color color,int width,int height,boolean disabled) {this.kind=kind;this.color=color;this.width=width;this.height=height;this.disabled=disabled;}
    @Override public int getIconWidth(){return width;}
    @Override public int getIconHeight(){return height;}
    @Override public void paintIcon(Component c, Graphics g,int x,int y){
      Graphics2D a=(Graphics2D)g.create();
      try {
        a.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        a.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,RenderingHints.VALUE_STROKE_PURE);
        a.translate(x+(width-24)/2.0,y+(height-24)/2.0);
        a.setColor(disabled?color.darker():color);
        a.setStroke(new BasicStroke(1.8f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        paintSymbol(a,kind);
      } finally {a.dispose();}
    }
  }

  private static void paintSymbol(Graphics2D g,String kind){
    switch(kind){
      case "calendar" -> {
        g.drawRoundRect(3,4,18,17,3,3);
        g.drawLine(3,9,21,9);
        g.drawLine(8,2,8,6);g.drawLine(16,2,16,6);
        g.fillRoundRect(7,12,3,3,1,1);
        g.fillRoundRect(13,12,3,3,1,1);
        g.fillRoundRect(7,17,3,2,1,1);
        g.fillRoundRect(13,17,3,2,1,1);
      }
      case "chart" -> {
        g.drawLine(3,21,21,21);
        g.drawRoundRect(5,13,3,7,1,1);
        g.drawRoundRect(11,9,3,11,1,1);
        g.drawRoundRect(17,4,3,16,1,1);
      }
      case "sliders" -> {
        g.drawLine(3,6,21,6);g.drawLine(3,12,21,12);g.drawLine(3,18,21,18);
        g.setColor(g.getColor());
        g.fillOval(7,3,6,6);g.fillOval(14,9,6,6);g.fillOval(5,15,6,6);
        g.setColor(new Color(21,36,59));
        g.fillOval(9,5,2,2);g.fillOval(16,11,2,2);g.fillOval(7,17,2,2);
      }
      case "folder" -> {
        Path2D.Double folder=new Path2D.Double();
        folder.moveTo(2.5,8);folder.lineTo(2.5,19);
        folder.quadTo(2.5,21,5,21);folder.lineTo(19.5,21);
        folder.quadTo(21.5,21,21.5,19);folder.lineTo(21.5,10);
        folder.quadTo(21.5,8,19.5,8);folder.lineTo(11,8);
        folder.lineTo(9,5);folder.lineTo(5,5);
        folder.quadTo(2.5,5,2.5,8);folder.closePath();
        g.draw(folder);
        g.drawLine(3,11,21,11);
      }
      case "gear" -> {
        Path2D.Double gear=new Path2D.Double();
        for(int i=0;i<32;i++){
          double theta=-Math.PI/2+2*Math.PI*i/32;
          double r=(i%4==0||i%4==3)?10:7.8;
          double xx=12+Math.cos(theta)*r, yy=12+Math.sin(theta)*r;
          if(i==0)gear.moveTo(xx,yy);else gear.lineTo(xx,yy);
        }
        gear.closePath();g.draw(gear);g.drawOval(9,9,6,6);
      }
      case "database" -> {
        g.drawOval(4,4,16,6);g.drawOval(4,15,16,6);
        g.drawLine(4,7,4,18);g.drawLine(20,7,20,18);
        g.drawArc(4,9,16,6,180,180);
      }
      case "sidebar" -> {
        g.drawRoundRect(2,3,20,18,3,3);g.drawLine(9,3,9,21);
        g.drawLine(4,9,7,9);g.drawLine(4,13,7,13);
      }
      default -> g.drawRoundRect(4,4,16,16,3,3);
    }
  }
}
