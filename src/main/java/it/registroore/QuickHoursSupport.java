package it.registroore;

/** Invarianti per le modifiche rapide di 30 minuti nel Registro. */
final class QuickHoursSupport {
  static final double STEP = 0.5;
  static final double EPSILON = 0.000001;
  private QuickHoursSupport() {}

  /** Il valore zero non cancella silenziosamente una registrazione: usare Elimina. */
  static double adjusted(double previous, double dailyTotal, double yearlyTotal, double delta) {
    if (!Double.isFinite(previous) || !Double.isFinite(dailyTotal) ||
        !Double.isFinite(yearlyTotal) || !Double.isFinite(delta) ||
        Math.abs(Math.abs(delta)-STEP)>EPSILON)
      throw new IllegalArgumentException("Incremento non valido: sono ammessi solo passi di 30 minuti.");
    double next = Math.round((previous+delta)*1000000.0)/1000000.0;
    if (next <= EPSILON)
      throw new IllegalArgumentException("Non si può scendere a zero ore con il pulsante −. Usa Modifica o Elimina.");
    if (dailyTotal+delta > 24.0+EPSILON)
      throw new IllegalArgumentException("La giornata supererebbe 24 ore.");
    if (yearlyTotal+delta > 1500.0+EPSILON)
      throw new IllegalArgumentException("Il monte ore annuale supererebbe 1.500 ore.");
    return next;
  }
}
