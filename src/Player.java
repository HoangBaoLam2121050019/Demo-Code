public class Player {
    private int x;
    private int y;

    public Player(int startX, int startY) {
        this.x = startX;
        this.y = startY;
    }

    public void BreakBlock(World world) {
        Block block = world.getBlockForPosition(x, y);
        if (block.isBreakable()) {
            world.setBlock(x, y, new AirBlock());
            System.out.println("Broke " + block.getName() + " block.");
        } else {
            System.out.println(block.getName() + " block cannot be broken.");
        }
    }

    public void move(char direction, World world) {
        int newX = x;
        int newY = y;
        switch (direction) {
            case 'W':
                newY--;
                break;
            case 'S':
                newY++;
                break;
            case 'A':
                newX--;
                break;
            case 'D':
                newX++;
                break;
        }
        Block target = world.getBlockForPosition(newX, newY);
        if (target != null && target.isWalkable()) {
            x = newX;
            y = newY;
            System.out.println("Moved to (" + x + ", " + y + ")");
        } else {
            System.out.println("Cannot move to (" + newX + ", " + newY + ")");
        }
    }
}