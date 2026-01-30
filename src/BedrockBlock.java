public class BedrockBlock extends Block {
    public BedrockBlock() {
        this.name = "Bedrock";
        this.breakable = false;
    }
    @Override
    public char getSymbol() {
        return 'B';
    }
}