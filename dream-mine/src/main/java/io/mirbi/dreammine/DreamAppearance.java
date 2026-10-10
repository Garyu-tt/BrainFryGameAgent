package io.mirbi.dreammine;

/** Input validation only; no live skin fetching in this class. */
public final class DreamAppearance {
    private DreamAppearance(){}
    public static boolean validName(String name) {
        return name != null && name.matches("[A-Za-z0-9_]{1,32}");
    }
    public static boolean validSkin(String account){
        return account != null && account.matches("[A-Za-z0-9_]{1,16}");
    }
    public record SkinName(String nick,String account) {}
    public static SkinName fromEggLabel(String label) {
        if(label==null || label.isBlank())return new SkinName("Dream","Dream");
        String[] parts=label.split("@",-1);
        if(parts.length==2 && validName(parts[0]) && validSkin(parts[1]))
            return new SkinName(parts[0],parts[1]);
        if(parts.length==1 && validSkin(label))return new SkinName(label,label);
        if(parts.length==1 && validName(label))return new SkinName(label,"Dream");
        return new SkinName("Dream","Dream");
    }
}
