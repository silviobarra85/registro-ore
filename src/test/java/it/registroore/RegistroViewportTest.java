package it.registroore;

/** Verifica logica senza dipendenze esterne: java -cp ... it.registroore.RegistroViewportTest */
public final class RegistroViewportTest {
  static void check(int expected, int actual) {
    if(expected!=actual) throw new AssertionError("Atteso "+expected+" ottenuto "+actual);
  }
  public static void main(String[] args) {
    // 20 ottobre e' visibile 85 px sotto l'alto del viewport, poi una riga
    // precedente aumenta di 40 px: giorno stesso rimane a 85 px.
    check(995, App.registerViewportTarget(955,1080,85,2400,true));
    // Giorno modificato ancora nella stessa posizione: non deve scorrere.
    check(955, App.registerViewportTarget(955,1040,85,2400,true));
    // Eliminazione: righe precedenti piu' corte; compensa anche verso l'alto.
    check(915, App.registerViewportTarget(955,1000,85,2400,true));
    // Nessuna ancora: mantieni esattamente la vecchia posizione.
    check(955, App.registerViewportTarget(955,0,0,2400,false));
    // Limiti del viewport dopo una modifica che accorcia il registro.
    check(700, App.registerViewportTarget(955,1040,85,700,true));
    check(0, App.registerViewportTarget(0,-500,70,2400,true));
    System.out.println("OK - posizione Registro conservata al cambio di ore/attivita/eliminazione");
  }
}
