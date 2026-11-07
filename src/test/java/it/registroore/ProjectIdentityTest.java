package it.registroore;
import java.lang.reflect.*;

/** Test leggero delle etichette: non richiede la connessione SQLite. */
public class ProjectIdentityTest {
  public static void main(String[] args) throws Exception {
    Method acronym=App.class.getDeclaredMethod("defaultAcronym",String.class);
    acronym.setAccessible(true);
    if(!"GATT".equals(acronym.invoke(null,"PROGETTO GATT")))throw new AssertionError("acronimo GATT");
    if(!"ATHENA".equals(acronym.invoke(null,"PROGETTO ATHENA")))throw new AssertionError("acronimo ATHENA");
    Class<?> activity=Class.forName("it.registroore.App$Activity");
    Class<?> brand=Class.forName("it.registroore.App$ProjectBrand");
    Constructor<?> ac=activity.getDeclaredConstructor(int.class,String.class,String.class,String.class);
    Constructor<?> bc=brand.getDeclaredConstructor(String.class,String.class,String.class);
    ac.setAccessible(true);bc.setAccessible(true);
    Object a=ac.newInstance(45,"PROGETTO GATT","PROGETTI","#D0E0FF");
    Object b=bc.newInstance("FRA 2022","GATT","GAmification in Testing Teaching");
    Method title=App.class.getDeclaredMethod("projectFullTitle",activity,brand);
    title.setAccessible(true);
    String result=(String)title.invoke(null,a,b);
    if(!result.equals("GATT — GAmification in Testing Teaching"))throw new AssertionError(result);
    System.out.println("OK: etichette progetto");
  }
}
