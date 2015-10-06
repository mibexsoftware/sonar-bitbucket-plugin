package ch.mycompany.test.gui;

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
        System.out.println( "Hello World!" );
    }

    private void untested() {
        try {
            System.out.println( "Hello World!" );
        } catch(Exception e) {
            int i = 42;
            double d = Double.longBitsToDouble(i);  // Noncompliant
        }
    }

    private void foo() {
        int i = 100023;
        System.exit(-1);
    }
    
    private void bar() {
        int baba = 5555;
        //TODO fix this
    }

    private void foobar() {
        int gugus = 6666;
        //FIXME fix this
        float pi = 3.1415f;
        double e = 2.718;
    }
}
