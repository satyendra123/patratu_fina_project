public class TestDllLoad {
  public static void main(String[] args) {
    String p = "C:\\Users\\HP\\Downloads\\apache-tomcat-9.0.115-windows-x64 (1)\\apache-tomcat-9.0.115\\bin\\mssql-jdbc_auth-13.2.1.x64.dll";
    try {
      System.out.println("Loading: " + p);
      System.load(p);
      System.out.println("Loaded OK");
    } catch (Throwable t) {
      System.out.println("LOAD FAILED: " + t);
      t.printStackTrace(System.out);
    }
  }
}
