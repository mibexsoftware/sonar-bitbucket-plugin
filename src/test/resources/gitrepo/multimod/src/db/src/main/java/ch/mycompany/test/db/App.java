package ch.mycompany.test.db;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args )
    {
        int i = 42;
        double d = Double.longBitsToDouble(i);  // Noncompliant
        try {
        } catch (Exception e) {
        }
        System.out.println( "Hello World! " + d);
    }
}
