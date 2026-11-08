#!/usr/bin/env bash
# Registro Ore 3.11.3 Mint FIX1 - avvio senza sostituire database esistenti.
set -Eeuo pipefail
umask 077
REAL_SCRIPT="$(readlink -f "$0")"
APP_DIR="$(cd "$(dirname "$REAL_SCRIPT")" && pwd)"
DATA_ROOT="${XDG_DATA_HOME:-$HOME/.local/share}/RegistroOre"
DATA_DIR="$DATA_ROOT/data"
LIB_DIR="$DATA_ROOT/lib"
LOG_DIR="$DATA_ROOT/logs"
mkdir -p "$DATA_DIR" "$LIB_DIR" "$LOG_DIR"
LOG_FILE="$LOG_DIR/registro_ore_$(date +%Y%m%d).log"
error_dialog() {
  local msg="$1"
  echo "ERRORE: $msg" >&2
  if command -v zenity >/dev/null 2>&1 && { [ -n "${DISPLAY:-}" ] || [ -n "${WAYLAND_DISPLAY:-}" ]; }; then
    zenity --error --title='Registro Ore' --text="$msg" --width=460 >/dev/null 2>&1 || true
  elif command -v kdialog >/dev/null 2>&1 && { [ -n "${DISPLAY:-}" ] || [ -n "${WAYLAND_DISPLAY:-}" ]; }; then
    kdialog --error "$msg" --title 'Registro Ore' >/dev/null 2>&1 || true
  fi
}
if ! command -v python3 >/dev/null 2>&1; then error_dialog 'Python 3 e richiesto dal gestore di avvio.'; exit 1; fi
MODE=start
case "${1:-}" in
  --inizializza) MODE=init ;;
  --diagnostica) MODE=check ;;
  --prepara) MODE=prepare ;;
  '' ) ;;
  *) echo 'Uso: registro-ore [--inizializza | --diagnostica | --prepara]' >&2; exit 2 ;;
esac
if [ "$MODE" != 'check' ] && [ "$MODE" != 'init' ]; then
  if ! command -v java >/dev/null 2>&1; then
    error_dialog 'Java 17 o successivo non trovato. Apri Gestore Applicazioni e installa OpenJDK 17 o 21.'; exit 1
  fi
  JAVA_VERSION=$(java -version 2>&1 | head -1)
  JAVA_MAJOR=$(printf '%s' "$JAVA_VERSION" | sed -nE 's/.*version "(1\.)?([0-9]+).*/\2/p')
  if ! [[ "$JAVA_MAJOR" =~ ^[0-9]+$ ]] || [ "$JAVA_MAJOR" -lt 17 ]; then
    error_dialog "Serve Java 17 o successivo; rilevata: $JAVA_VERSION"; exit 1
  fi
fi
BOOTSTRAP_LOG="$LOG_DIR/primo_avvio.log"
if ! python3 "$APP_DIR/bootstrap.py" --app-dir "$APP_DIR" --data-dir "$DATA_ROOT" --mode "$MODE" >"$BOOTSTRAP_LOG" 2>&1; then
  error_dialog "Preparazione non riuscita. Controlla il log:\n$BOOTSTRAP_LOG\n\n$(tail -n 4 "$BOOTSTRAP_LOG")"
  exit 1
fi
if [ "$MODE" != 'start' ]; then
  cat "$BOOTSTRAP_LOG"
  exit 0
fi
DB="$DATA_DIR/registro_ore_2026.sqlite"
if ! java -Dfile.encoding=UTF-8 "-Dregistro.db=$DB" -cp "$APP_DIR/RegistroOre.jar:$LIB_DIR/*" it.registroore.App >>"$LOG_FILE" 2>&1; then
  error_dialog "Registro Ore non si e avviato correttamente. Log:\n$LOG_FILE\n\n$(tail -n 4 "$LOG_FILE")"
  exit 1
fi
