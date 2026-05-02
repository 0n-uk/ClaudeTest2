import java.io.*;
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

    // ── Static loading ────────────────────────────────────────────────────────

    public static Map<String, ChampionLine> loadAll() {
        return loadAll("champions.txt");
    }

    public static Map<String, ChampionLine> loadAll(String filename) {
        Map<String, List<Champion>> buckets = new LinkedHashMap<>();
        try (BufferedReader r = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                Champion c = parseLine(line);
                if (c != null)
                    buckets.computeIfAbsent(c.getLineId(), k -> new ArrayList<>()).add(c);
            }
        } catch (IOException ignored) {}

        Map<String, ChampionLine> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<Champion>> e : buckets.entrySet()) {
            List<Champion> sorted = e.getValue();
            sorted.sort(Comparator.comparingInt(Champion::getStage));
            result.put(e.getKey(), new ChampionLine(e.getKey(), sorted));
        }
        return result;
    }

    private static Champion parseLine(String line) {
        try {
            String id      = extract(line, "id='",      "'");
            String name    = extract(line, "name='",    "'");
            String type    = extract(line, "type='",    "'");
            int    attack  = Integer.parseInt(extract(line, "attack=", ","));
            int    hp      = Integer.parseInt(extract(line, "hp=",     ","));
            int    stage   = Integer.parseInt(extract(line, "stage=",  ","));
            String lineId  = extract(line, "line='",   "'");
            String ability = extract(line, "ability='", "'");
            return new Champion(id, name, type, attack, hp, stage, lineId, ability);
        } catch (Exception e) {
            return null;
        }
    }

    private static String extract(String line, String after, String before) {
        int start = line.indexOf(after);
        if (start < 0) return "";
        start += after.length();
        int end = line.indexOf(before, start);
        if (end < 0) return line.substring(start).trim();
        return line.substring(start, end).trim();
    }
}
