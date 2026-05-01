public class Champion extends Card {

    private int    stage;
    private String lineId;

    public Champion(String id, String name, String type, int attack, int hp,
                    int stage, String lineId, String ability) {
        super(id, name, type, attack, hp, 0, ability);
        this.stage  = stage;
        this.lineId = lineId;
    }

    public int    getStage()  { return stage; }
    public String getLineId() { return lineId; }

    public boolean isFinalStage(ChampionLine line) {
        return stage == line.size();
    }

    @Override
    public String toString() {
        return "Champion{id='" + getId() + "', name='" + getName() + "', type='" + getType()
             + "', attack=" + getAttack() + ", hp=" + getHp()
             + ", stage=" + stage + ", line='" + lineId + "', ability='" + getAbility() + "'}";
    }
}
