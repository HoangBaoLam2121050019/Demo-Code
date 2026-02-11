import java.util.Scanner;

public class Game {
    public static void main(String[] args) {
        World world = new World(100, 100);
        Player player = new Player(5, 7);

        System.out.println("=== Initial World ===");
        world.printWorld();

        player.BreakBlock(world);

        System.out.println("\n=== World after breaking block ===");
        world.printWorld();

        System.out.println("\n=== Game Started ===");
        System.out.println("Controls: W/A/S/D to move, B to break block, Q to quit\n");

        try (Scanner sc = new Scanner(System.in)) {
            while (true) {
                world.printWorld();
                System.out.println("\nPlayer position: (" + player.getX() + ", " + player.getY() + ")");
                System.out.print("Move (WASD/B/Q): ");

                String inputStr = sc.nextLine().toUpperCase();
                if (inputStr.isEmpty()) {
                    System.out.println("Invalid input. Please enter W, A, S, D, B, or Q.\n");
                    continue;
                }

                char input = inputStr.charAt(0);

                switch (input) {
                    case 'Q':
                        System.out.println("Thanks for playing!");
                        return;
                    case 'B':
                        player.BreakBlock(world);
                        break;
                    case 'W':
                    case 'A':
                    case 'S':
                    case 'D':
                        player.move(input, world);
                        break;
                    default:
                        System.out.println("Invalid input. Please enter W, A, S, D, B, or Q.\n");
                }
                System.out.println();
            }
        }
    }
}