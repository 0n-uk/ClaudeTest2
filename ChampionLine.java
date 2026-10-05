import java.util.*;
import java.util.stream.Collectors;

public class ChampionLine {

    private final String                  lineId;
    private final List<Champion>          stages;
    private final Map<Integer, Champion>  stageMap;

    public ChampionLine(String lineId, List<Champion> stages) {
        this.lineId = lineId;
        this.stages = Collections.unmodifiableList(new ArrayList<>(stages));
        this.stageMap = stages.stream().collect(Collectors.toMap(Champion::getStage, c -> c));
    }

    public String         getLineId()  { return lineId; }
    public List<Champion> getStages()  { return stages; }
    public int            size()       { return stages.size(); }

    public Champion getStage(int stage) {
        return stageMap.get(stage);
    }

    public Champion getStageByIndex(int idx) {
        return (idx >= 0 && idx < stages.size()) ? stages.get(idx) : null;
    }

    public String getLineName() {
        return stages.isEmpty() ? lineId : stages.get(0).getName() + " line";
    }
}
