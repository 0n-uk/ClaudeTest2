class Card:
    def __init__(self, id, name, type, attack, hp, cost, ability):
        self.id = id
        self.name = name
        self.type = type
        self.attack = attack
        self.hp = hp
        self.cost = cost
        self.ability = ability

    def __repr__(self):
        return (
            f"Card(id={self.id!r}, name={self.name!r}, type={self.type!r}, "
            f"attack={self.attack}, hp={self.hp}, cost={self.cost}, ability={self.ability!r})"
        )
