package it.registroore;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.math.BigDecimal;

/** Presentation and note handling for additive multi-day timesheet entries.
 * The functions are pure, so they can be regression tested without Swing or SQLite.
 */
final class BatchEntrySupport {
  private BatchEntrySupport() {}

  static String hours(double value) {
    return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString().replace('.', ',');
  }

  static double total(List<App.LogEntry> rows) {
    return rows.stream().filter(e -> e.hours() != null).mapToDouble(App.LogEntry::hours).sum();
  }

  static double activityHours(List<App.LogEntry> rows, String activity) {
    if (activity == null || activity.isBlank()) return 0.0;
    return rows.stream().filter(e -> e.hours() != null && e.activity().name().equalsIgnoreCase(activity.trim()))
        .mapToDouble(App.LogEntry::hours).sum();
  }

  static String dayLabel(LocalDate day, boolean selected, String activity, List<App.LogEntry> rows) {
    double matched = activityHours(rows, activity);
    double daily = total(rows);
    String quantity = !activity.isBlank() ? (matched > 0 ? hours(matched) + " h" : "—")
        : (daily > 0 ? "Σ " + hours(daily) + " h" : "—");
    return "<html><center>" + (selected ? "&#10003; " : "") + day.getDayOfMonth()
        + "<br><small>" + quantity + "</small></center></html>";
  }

  static String escaped(String raw) {
    if (raw == null) return "";
    return raw.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&#39;");
  }

  static String tooltip(LocalDate day, List<App.LogEntry> rows, String activity, Double proposed,
                        boolean locked, String reason) {
    StringBuilder sb = new StringBuilder("<html><div style='width:300px'><b>")
        .append(day.format(DateTimeFormatter.ofPattern("EEEE d MMMM uuuu", Locale.ITALY)))
        .append("</b><br>Totale registrato: <b>").append(hours(total(rows))).append(" h</b>");
    if (!activity.isBlank()) {
      sb.append("<br>").append(escaped(activity)).append(": <b>")
        .append(hours(activityHours(rows, activity))).append(" h</b> già presenti");
    }
    if (locked) sb.append("<br><b>Mensilità bloccata</b>");
    if (reason != null && !reason.isBlank()) sb.append("<br>").append(escaped(reason));
    if (rows.isEmpty()) sb.append("<br>Nessuna registrazione");
    else {
      sb.append("<hr><b>Dettaglio giornata</b>");
      for (App.LogEntry e : rows) {
        sb.append("<br>&bull; ").append(escaped(e.activity().name()))
          .append(": ").append(e.hours() == null ? "—" : hours(e.hours()) + " h");
        if (e.note() != null && !e.note().isBlank()) {
          String note = e.note().length() > 110 ? e.note().substring(0, 110) + "…" : e.note();
          sb.append(" — ").append(escaped(note));
        }
        if (e.missionId() != null) sb.append(" (missione #").append(e.missionId()).append(")");
      }
    }
    if (proposed != null && proposed > 0) {
      double planned = total(rows) + proposed;
      sb.append("<hr>Dopo l'aggiunta: <b>").append(hours(planned)).append(" h</b>");
      if (planned > 24.000001) sb.append(" — <b>supera 24 h: non ammesso</b>");
      else if (planned > 8.000001) sb.append(" — oltre 8 h: verifica");
    }
    return sb.append("</div></html>").toString();
  }

  /** Keep distinct descriptions when multiple compatible lines are consolidated. */
  static String mergeNotes(List<String> previous, String incoming) {
    LinkedHashSet<String> parts = new LinkedHashSet<>();
    for (String note : previous) {
      if (note != null && !note.isBlank()) parts.add(note.trim());
    }
    if (incoming != null && !incoming.isBlank()) parts.add(incoming.trim());
    return String.join(" | ", parts);
  }
}
