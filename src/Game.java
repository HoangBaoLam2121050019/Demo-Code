import java.util.Scanner;
public class Game {
    public static void main(String[] args) {
        World world = new World(100, 100);
        Player player = new Player(5, 7);
        System.out.println("Initial World:");
        world.printWorld();
        player.BreakBlock(world);
        System.out.println("World after breaking block:");
        world.printWorld();

        Scanner sc = new Scanner(System.in);
        while (true) {
            world.printWorld();
            System.out.print("Move  (WASD): ");
            String inputStr = sc.nextLine().toUpperCase();
            if (inputStr.isEmpty()) {
                System.out.println("Invalid input. Please enter W, A, S, or D.");
                continue;
            }
            char input = inputStr.charAt(0);
            player.move(input, world);
        }
    }
}