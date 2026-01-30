public class Game {
    public static void main(String[] args) {
        World world = new World(10, 10);
        Player player = new Player(5, 7);
        System.out.println("Initial World:");
        world.printWorld();
        player.BreakBlock(world);
        System.out.println("World after breaking block:");
        world.printWorld();
    }
}
