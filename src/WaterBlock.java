public class WaterBlock extends Block {
    public WaterBlock() {
        this.name = "Water";
        this.breakable = false;
    }
    @Override
    public char getSymbol() {
        return '~';
    }
}