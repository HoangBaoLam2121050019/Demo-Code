public class Player {
    private int x;
    private int y;

    public Player(int startX, int startY) {
        this.x = startX;
        this.y = startY;
    }

    public void BreakBlock(World world) {
        Block block = world.getBlock(x, y);
        if (block.isBreakable()) {
            world.setBlock(x, y, new AirBlock());
            System.out.println("Broke " + block.getName() + " block.");
        } else {
            System.out.println(block.getName() + " block cannot be broken.");
        }
    }
}