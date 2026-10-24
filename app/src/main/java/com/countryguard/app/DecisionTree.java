package com.countryguard.app;

import java.util.*;

public final class DecisionTree {
    public enum Kind { OUTCOME, CONTACT, NON_CONTACT, TIME, COUNTRY, NUMBERS, PREFIX, TEMPORARY, REPEAT, LINK, PHRASE }
    public static final class Node {
        public String id=UUID.randomUUID().toString();
        public Kind kind;
        public ProtectionPolicy.Action action=ProtectionPolicy.Action.ALLOW;
        public Node yes,no;
        public Set<String> values=new TreeSet<>();
        public int start=480,end=1080,limit=2,window=10,legacyStage=-1;
        public boolean outside=false;
        public String reason="";
        public Node(Kind kind){this.kind=kind;}
    }
    public record Step(String id,String question,boolean yes) {}
    public record Result(ProtectionPolicy.Action action,String terminalId,String reason,int stage,List<Step> path) {}
    public interface Facts { boolean matches(Node node); }
    public static Node outcome(ProtectionPolicy.Action action){Node n=new Node(Kind.OUTCOME);n.action=action;return n;}
    public static Node decision(Kind kind,Node yes,Node no){Node n=new Node(kind);n.yes=yes;n.no=no;return n;}
    public static Result evaluate(Node root,boolean sms,Facts facts){
        validate(root,sms);List<Step> path=new ArrayList<>();Node current=root;
        while(current.kind!=Kind.OUTCOME){boolean yes=facts.matches(current);path.add(new Step(current.id,title(current),yes));current=yes?current.yes:current.no;}
        String why=current.reason;
        if(why.isEmpty())why=path.isEmpty()?"Tree ends immediately":path.get(path.size()-1).question()+" → "+(path.get(path.size()-1).yes()?"Yes":"No");
        return new Result(current.action,current.id,why,current.legacyStage,List.copyOf(path));
    }
    public static void validate(Node root,boolean sms){validate(root,sms,new HashSet<>(),new HashSet<>(),0);}
    private static void validate(Node n,boolean sms,Set<Node> seen,Set<String> ids,int depth){
        if(n==null||n.kind==null||n.id==null||!seen.add(n)||!ids.add(n.id))throw new IllegalArgumentException("Every branch must have its own node");
        if(depth>24||seen.size()>255)throw new IllegalArgumentException("Tree limit: 255 nodes and 24 decisions per path");
        if(n.kind==Kind.OUTCOME){
            if(n.action==null||sms&&n.action==ProtectionPolicy.Action.SILENCE)throw new IllegalArgumentException("Invalid outcome");
            if(n.yes!=null||n.no!=null)throw new IllegalArgumentException("Outcomes cannot have branches");return;
        }
        if(sms&&n.kind==Kind.REPEAT||!sms&&(n.kind==Kind.LINK||n.kind==Kind.PHRASE))throw new IllegalArgumentException("Condition does not apply to this tree");
        if(n.start<0||n.start>=1440||n.end<0||n.end>=1440||n.limit<1||n.limit>99||n.window<1||n.window>60)throw new IllegalArgumentException("Invalid condition settings");
        validate(n.yes,sms,seen,ids,depth+1);validate(n.no,sms,seen,ids,depth+1);
    }
    public static String clock(int t){return String.format(Locale.ROOT,"%02d:%02d",t/60,t%60);}
    public static String title(Node n){return switch(n.kind){
        case OUTCOME -> n.action==ProtectionPolicy.Action.BLOCK?"Block":n.action==ProtectionPolicy.Action.SILENCE?"Silence":"Allow";
        case CONTACT -> "Saved contact?";
        case NON_CONTACT -> "Confirmed non-contact?";
        case TIME -> (n.outside?"Outside ":"Inside ")+clock(n.start)+"–"+clock(n.end)+"?";
        case COUNTRY -> n.outside?"Outside selected countries?":"In selected countries?";
        case NUMBERS -> "Number / sender matches?";
        case PREFIX -> "Number starts with…?";
        case TEMPORARY -> "Temporary permission active?";
        case REPEAT -> "More than "+n.limit+" attempts in "+n.window+" min?";
        case LINK -> "Message contains a link?";
        case PHRASE -> "Message contains a phrase?";
    };}
    public static String detail(Node n){return switch(n.kind){
        case COUNTRY,NUMBERS,PREFIX,PHRASE -> n.values.isEmpty()?"Empty selection":n.values.size()<=2?String.join(", ",n.values):n.values.size()+" selected";
        case OUTCOME -> "Tap to change or add a decision";
        case CONTACT,NON_CONTACT -> "Phone address book";
        case TIME -> "Every day · Phone time";
        default -> "Tap to configure";
    };}
    public static Node find(Node root,String id){if(root.id.equals(id))return root;if(root.kind==Kind.OUTCOME)return null;Node y=find(root.yes,id);return y==null?find(root.no,id):y;}
    public static Node replace(Node root,String id,Node replacement){if(root.id.equals(id))return replacement;if(root.kind!=Kind.OUTCOME){root.yes=replace(root.yes,id,replacement);root.no=replace(root.no,id,replacement);}return root;}
}
