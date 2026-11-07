# Compilare il launcher Windows

`main.go` e' il launcher Go che apre l'app Java. Il pacchetto deve incorporare il `RegistroOre.jar` aggiornato e il database vergine (mai quello personale).

Da una macchina con Go installato, dopo `mvn -B clean package` nella root del progetto:

```bash
mkdir -p WindowsLauncher/assets
cp target/registro-ore-3.10.2.jar WindowsLauncher/assets/RegistroOre.jar
cp database_vuoto.sqlite WindowsLauncher/assets/database_vuoto.sqlite
cd WindowsLauncher
GOOS=windows GOARCH=amd64 CGO_ENABLED=0 go build -trimpath -ldflags='-s -w -H=windowsgui' -o RegistroOre.exe main.go
```

Il `.exe` avvia Java 17+, predisponendolo all'occorrenza al primo avvio. Non contiene dati personali. Il launcher non e' firmato digitalmente.
