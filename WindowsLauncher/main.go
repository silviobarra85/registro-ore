package main

import (
 "archive/zip"
 "bytes"
 "context"
 "crypto/sha256"
 "embed"
 "errors"
 "fmt"
 "io"
 "net/http"
 "os"
 "os/exec"
 "path/filepath"
 "regexp"
 "strconv"
 "strings"
 "syscall"
 "time"
 "unsafe"
)

//go:embed assets/RegistroOre.jar assets/database_vuoto.sqlite
var assets embed.FS
const version = "3.8.0"

func popup(message string) {
 user32 := syscall.NewLazyDLL("user32.dll")
 proc := user32.NewProc("MessageBoxW")
 title,_ := syscall.UTF16PtrFromString("Registro Ore "+version)
 text,_ := syscall.UTF16PtrFromString(message)
 proc.Call(0,uintptr(unsafe.Pointer(text)),uintptr(unsafe.Pointer(title)),0x10)
}
func exists(path string) bool {f,e:=os.Stat(path);return e==nil&&!f.IsDir()&&f.Size()>0}
func writeAtomic(path string,data []byte)error{
 if err:=os.MkdirAll(filepath.Dir(path),0700);err!=nil{return err}
 tmp:=path+".nuovo"
 if err:=os.WriteFile(tmp,data,0600);err!=nil{return err}
 return os.Rename(tmp,path)
}
func extractAsset(rel,dest string,replace bool)error{
 data,err:=assets.ReadFile("assets/"+rel);if err!=nil{return err}
 if !replace && exists(dest){return nil}
 if replace&&exists(dest){old,err:=os.ReadFile(dest);if err==nil&&sha256.Sum256(old)==sha256.Sum256(data){return nil}}
 return writeAtomic(dest,data)
}
func downloadZip(url,path string,max int64)error{
 if exists(path){return nil}
 client:=http.Client{Timeout:10*time.Minute}
 ctx,cancel:=context.WithTimeout(context.Background(),10*time.Minute);defer cancel()
 req,err:=http.NewRequestWithContext(ctx,"GET",url,nil);if err!=nil{return err}
 resp,err:=client.Do(req);if err!=nil{return fmt.Errorf("connessione: %w",err)};defer resp.Body.Close()
 if resp.StatusCode!=200{return fmt.Errorf("HTTP %d: %s",resp.StatusCode,url)}
 data,err:=io.ReadAll(io.LimitReader(resp.Body,max+1));if err!=nil{return err}
 if int64(len(data))>max{return errors.New("download troppo grande")}
 if len(data)<1000{return errors.New("download incompleto")}
 if _,err=zip.NewReader(bytes.NewReader(data),int64(len(data)));err!=nil{return fmt.Errorf("archivio non valido: %w",err)}
 return writeAtomic(path,data)
}
func javaMajor(binary string)int{
 name:=binary
 if strings.EqualFold(filepath.Base(name),"javaw.exe"){name=filepath.Join(filepath.Dir(name),"java.exe")}
 ctx,cancel:=context.WithTimeout(context.Background(),8*time.Second);defer cancel()
 out,err:=exec.CommandContext(ctx,name,"-version").CombinedOutput();if err!=nil{return 0}
 m:=regexp.MustCompile(`version\s+"(?:1\.)?(\d+)`).FindStringSubmatch(string(out));if len(m)<2{return 0}
 n,_:=strconv.Atoi(m[1]);return n
}
func findJava(root string)(string,error){
 candidates:=[]string{}
 for _,home:=range []string{os.Getenv("JAVA_HOME"),os.Getenv("JDK_HOME")} {
  if home!=""{candidates=append(candidates,filepath.Join(home,"bin","javaw.exe"))}
 }
 if j,err:=exec.LookPath("javaw.exe");err==nil{candidates=append(candidates,j)}
 for _,base:=range []string{`C:\Program Files\Eclipse Adoptium`,`C:\Program Files\Java`,`C:\Program Files\Microsoft`}{
  dirs,_:=os.ReadDir(base);for _,d:=range dirs {if d.IsDir(){candidates=append(candidates,filepath.Join(base,d.Name(),"bin","javaw.exe"))}}
 }
 for _,p:=range candidates{if exists(p)&&javaMajor(p)>=17{return p,nil}}
 cache:=filepath.Join(root,"runtime")
 filepath.Walk(cache,func(path string,info os.FileInfo,err error)error{
  if err==nil&&info!=nil&&info.Name()=="javaw.exe"&&strings.EqualFold(filepath.Base(filepath.Dir(path)),"bin"){
   if javaMajor(path)>=17{candidates=append(candidates,path)}
  }
  return nil
 })
 for _,p:=range candidates{if exists(p)&&javaMajor(p)>=17{return p,nil}}
 // Download a personal JRE17 only if the user has no compatible Java on Windows.
 archive:=filepath.Join(root,"lib","jre17-windows-x64.zip")
 if err:=downloadZip("https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jre/hotspot/normal/eclipse",archive,210*1024*1024);err!=nil{return "",fmt.Errorf("Java 17 non trovato e download automatico impossibile: %w",err)}
 f,err:=os.Open(archive);if err!=nil{return "",err};defer f.Close();fi,err:=f.Stat();if err!=nil{return "",err}
 r,err:=zip.NewReader(f,fi.Size());if err!=nil{return "",err}
 for _,e:=range r.File{
  safe:=filepath.Clean(e.Name)
  if strings.HasPrefix(safe,"..")||filepath.IsAbs(safe)||strings.Contains(safe,":") {return "",errors.New("archivio JRE contiene percorsi non sicuri")}
  dest:=filepath.Join(cache,safe)
  if e.FileInfo().IsDir(){if err:=os.MkdirAll(dest,0700);err!=nil{return "",err};continue}
  if err:=os.MkdirAll(filepath.Dir(dest),0700);err!=nil{return "",err}
  reader,err:=e.Open();if err!=nil{return "",err}
  output,err:=os.OpenFile(dest,os.O_CREATE|os.O_WRONLY|os.O_TRUNC,0600);if err!=nil{reader.Close();return "",err}
  _,copyErr:=io.Copy(output,io.LimitReader(reader,300*1024*1024));closeErr:=output.Close();reader.Close()
  if copyErr!=nil{return "",copyErr};if closeErr!=nil{return "",closeErr}
 }
 filepath.Walk(cache,func(path string,info os.FileInfo,err error)error{
  if err==nil&&info!=nil&&info.Name()=="javaw.exe"&&strings.EqualFold(filepath.Base(filepath.Dir(path)),"bin") {if javaMajor(path)>=17{candidates=append(candidates,path)}}
  return nil
 })
 for _,p:=range candidates{if exists(p)&&javaMajor(p)>=17{return p,nil}}
 return "",errors.New("Il download di Java 17 non contiene javaw.exe. Installa Temurin JRE 17 manualmente.")
}
func run()error{
 dir:=os.Getenv("LOCALAPPDATA");if dir==""{dir=os.TempDir()}
 root:=filepath.Join(dir,"RegistroOre")
 db:=filepath.Join(root,"data","registro_ore_2026.sqlite")
 jar:=filepath.Join(root,"app","RegistroOre.jar")
 if err:=extractAsset("database_vuoto.sqlite",db,false);err!=nil{return err}
 if err:=extractAsset("RegistroOre.jar",jar,true);err!=nil{return err}
 libs:=map[string]string{
  "sqlite-jdbc-3.49.1.0.jar":"https://repo.maven.apache.org/maven2/org/xerial/sqlite-jdbc/3.49.1.0/sqlite-jdbc-3.49.1.0.jar",
  "slf4j-api-2.0.17.jar":"https://repo.maven.apache.org/maven2/org/slf4j/slf4j-api/2.0.17/slf4j-api-2.0.17.jar",
  "slf4j-nop-2.0.17.jar":"https://repo.maven.apache.org/maven2/org/slf4j/slf4j-nop/2.0.17/slf4j-nop-2.0.17.jar",
 }
 for name,url:=range libs{if err:=downloadZip(url,filepath.Join(root,"lib",name),32*1024*1024);err!=nil{return fmt.Errorf("Impossibile preparare la libreria %s: %w",name,err)}}
 java,err:=findJava(root);if err!=nil{return err}
 logs:=filepath.Join(root,"logs");if err:=os.MkdirAll(logs,0700);err!=nil{return err}
 path:=filepath.Join(logs,"registro_ore_"+time.Now().Format("20060102")+".log")
 log,err:=os.OpenFile(path,os.O_CREATE|os.O_APPEND|os.O_WRONLY,0600);if err!=nil{return err};defer log.Close()
 args:=[]string{"-Dfile.encoding=UTF-8","-Dregistro.db="+db,"-cp",jar+";"+filepath.Join(root,"lib","*"),"it.registroore.App"}
 cmd:=exec.Command(java,args...);cmd.Stdout=log;cmd.Stderr=log;cmd.Dir=root
 if err:=cmd.Run();err!=nil{return fmt.Errorf("L'applicazione non si è avviata (%v). Controlla il log: %s",err,path)}
 return nil
}
func main(){if err:=run();err!=nil{popup(err.Error());os.Exit(1)}}
