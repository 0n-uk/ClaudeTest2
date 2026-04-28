public class Card {
    private int id;
    private String name;
    private String type;
    private int attack;
    private int hp;
    private int cost;
    private String ability;

    public Card(int id, String name, String type, int attack, int hp, int cost, String ability) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.attack = attack;
        this.hp = hp;
        this.cost = cost;
        this.ability = ability;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getType() { return type; }
    public int getAttack() { return attack; }
    public int getHp() { return hp; }
    public int getCost() { return cost; }
    public String getAbility() { return ability; }

    public void setId(int id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setType(String type) { this.type = type; }
    public void setAttack(int attack) { this.attack = attack; }
    public void setHp(int hp) { this.hp = hp; }
    public void setCost(int cost) { this.cost = cost; }
    public void setAbility(String ability) { this.ability = ability; }

    @Override
    public String toString() {
        return "Card{id=" + id + ", name='" + name + "', type='" + type + "', attack=" + attack
                + ", hp=" + hp + ", cost=" + cost + ", ability='" + ability + "'}";
    }
}
