import org.apache.commons.codec.binary.Base64;
public class T {
  public static void main(String[] a){
    byte[] data = new byte[300];
    for(int i=0;i<data.length;i++) data[i]=(byte)(i%256);
    String s = Base64.encodeBase64String(data);
    System.out.println(s.contains("\n") || s.contains("\r"));
    System.out.println(s.length());
  }
}
