public class CheckClasspath {
 public static void main(String[] args) throws Exception {
  Class<?> c=Class.forName("ru.rutcampustrack.shared.events.IdempotencyStore");
  System.out.println(c.getProtectionDomain().getCodeSource());
 }
}
