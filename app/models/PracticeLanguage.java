package models;

@lombok.Getter
public class PracticeLanguage extends Model {
    private static final long serialVersionUID = -1;

    private int id;
    private int level;

    public PracticeLanguage() {
    }

    public PracticeLanguage(int id, int level) {
        this.id = id;
        this.level = level;
    }
}
