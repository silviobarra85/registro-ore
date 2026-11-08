package it.registroore;

/** Test senza dipendenze Java aggiuntive. */
public final class QuickHoursSupportTest {
  static void same(double expected,double actual) { if(Math.abs(expected-actual)>1e-7) throw new AssertionError(expected+" != "+actual); }
  static void blocked(Runnable action) { try {action.run();throw new AssertionError("Era previsto un blocco");} catch(IllegalArgumentException expected){} }
  public static void main(String[] args) {
    same(3.5,QuickHoursSupport.adjusted(3,7,1300,0.5));
    same(2.5,QuickHoursSupport.adjusted(3,7,1300,-0.5));
    same(0.25,QuickHoursSupport.adjusted(0.75,3,80,-0.5));
    blocked(()->QuickHoursSupport.adjusted(0.5,4,200,-0.5));
    blocked(()->QuickHoursSupport.adjusted(5,24,200,0.5));
    blocked(()->QuickHoursSupport.adjusted(2,6,1500,0.5));
    blocked(()->QuickHoursSupport.adjusted(3,8,20,1));
    System.out.println("OK - variazioni rapide 30 minuti e limiti");
  }
}
