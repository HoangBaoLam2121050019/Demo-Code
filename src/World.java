public class World {
    private Block[][] grid;

    public World(int width, int height) {
        grid = new Block[height][width];
        generateWorld();
    }

    private void generateWorld() {
        for (int y = 0; y < grid.length; y++) {
            for(int x = 0; x < grid[y].length; x++) {
                if(y > grid.length/2){
                    grid[y][x] = new DirtBlock();
                } else {
                    grid[y][x] = new AirBlock();
                }
            }
        }
    }

    public Block getBlock(int x, int y) {
        return grid[y][x];
    }

    public void setBlock(int x, int y, Block block) {
        grid[y][x] = block;
    }

    public void printWorld() {
        for (Block[] row : grid) {
            for (Block block : row) {
                System.out.print(block.getSymbol());
            }
            System.out.println();
        }
    }
}