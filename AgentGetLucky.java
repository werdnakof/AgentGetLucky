import negotiator.AgentID;
import negotiator.Bid;
import negotiator.actions.Accept;
import negotiator.actions.Action;
import negotiator.actions.EndNegotiation;
import negotiator.actions.Offer;
import negotiator.issue.*;
import negotiator.parties.AbstractNegotiationParty;
import negotiator.parties.NegotiationInfo;
import negotiator.utility.AdditiveUtilitySpace;
import negotiator.utility.Evaluator;
import negotiator.utility.EvaluatorDiscrete;

import java.util.*;

/**
 * ExampleAgent returns the bid that maximizes its own utility for half of the negotiation session.
 * In the second half, it offers a random bid. It only accepts the bid on the table in this phase,
 * if the utility of the bid is higher than Example Agent's last bid.
 */
public class AgentGetLucky extends AbstractNegotiationParty {
    private final String description = "Example Agent";

    private Bid lastReceivedBid; // offer on the table
    private Bid myLastBid;
    private AdditiveUtilitySpace space;
    private List<Issue> issues;
    private Set<Map.Entry<Objective, Evaluator>> evaluators;
    private OpponentModel op1 = new OpponentModel();
    private OpponentModel op2 = new OpponentModel();
    private Preference pref = new Preference();
    private int roundCount = 0;
    private Double omega = 0.5;

    private class Preference {
        public HashMap<String, IssueWeight> weights = new HashMap<>();

        public void addIssue(IssueDiscrete issue, Double weighting) {
            IssueWeight iw = weights.getOrDefault(issue.getName(), new IssueWeight(weighting));
            weights.put(issue.getName(), iw);
        }

        public class IssueWeight {
            public Double weighting;
            public Integer maxValue = Integer.MIN_VALUE;
            private HashMap<String, Integer> evals = new HashMap<>();

            public IssueWeight(Double weighting) {
                this.weighting = weighting;
            }

            public void putWeight(ValueDiscrete value, Integer weight ) {
                this.evals.put(value.getValue(), weight);
                if(weight > maxValue) maxValue = weight;
            }

            public Integer getWeight(ValueDiscrete value) {
                return this.evals.get(value.getValue());
            }

            public Double getDisUtility(ValueDiscrete value) {
                Integer weight = this.getWeight(value);
                return weighting * (weight - this.maxValue) / this.maxValue;
            }
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, IssueWeight> entry: this.weights.entrySet()) {
                sb.append("\t" + entry.getKey() + ": " + entry.getValue().evals + "\n");
            }
            return sb.toString();
        }
    }

    // https://github.com/tdgunes/ExampleAgent/wiki/Accessing-the-evaluation-of-a-value
    @Override
    public void init(NegotiationInfo info) {
        super.init(info);
        this.space = (AdditiveUtilitySpace) this.getUtilitySpace();
        this.issues = this.space.getDomain().getIssues();
        this.evaluators = this.space.getEvaluators();

        for(Issue issue: this.issues) {
            int issueNumber = issue.getNumber();

            // Assuming that issues are discrete only
            IssueDiscrete issueDiscrete = (IssueDiscrete) issue;
            EvaluatorDiscrete evaluatorDiscrete = (EvaluatorDiscrete) this.space.getEvaluator(issueNumber);

            op1.addIssue(issueDiscrete);
            op2.addIssue(issueDiscrete);
            pref.addIssue(issueDiscrete, this.space.getWeight(issueNumber));

            OpponentModel.IssueStore is1 = op1.getIssue(issueDiscrete);
            OpponentModel.IssueStore is2 = op2.getIssue(issueDiscrete);

//            System.out.println("IssueName " + issueDiscrete.getName());

            for (ValueDiscrete valueDiscrete : issueDiscrete.getValues()) {

                System.out.println("ValueName " + valueDiscrete.getValue());
                System.out.println("Evaluation(getValue): " + evaluatorDiscrete.getValue(valueDiscrete));

                try {
                    System.out.println("Evaluation(getEvaluation): " + evaluatorDiscrete.getEvaluation(valueDiscrete));
                } catch (Exception e) {
                    e.printStackTrace();
                }

                is1.addValue(valueDiscrete);
                is2.addValue(valueDiscrete);

                try {
                    Integer vv = evaluatorDiscrete.getValue(valueDiscrete);
                    pref.weights
                        .get(issueDiscrete.getName())
                        .putWeight(valueDiscrete, vv);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            System.out.println(pref.toString());
        }
    }

    /**
     * When this function is called, it is expected that the Party chooses one of the actions from the possible
     * action list and returns an instance of the chosen action.
     *
     * @param list
     * @return
     */
    @Override
    public Action chooseAction(List<Class<? extends Action>> list) {

        double time = getTimeLine().getTime();

        if (time >= 1.0D) return new EndNegotiation(this.getPartyId());

        ++this.roundCount;
//        double timePerRound = this.timeline.getTime() / (double) this.roundCount;
//        double remainingRounds = (1.0D - this.timeline.getTime()) / timePerRound;

        if (lastReceivedBid != null &&
                myLastBid != null &&
                this.hasHigherUtility(lastReceivedBid, myLastBid)) {
            return new Accept(this.getPartyId(), lastReceivedBid);
        }

        if(time < 0.5D) {
            Offer max = new Offer(this.getPartyId(), this.getMaxUtilityBid());
            this.myLastBid = max.getBid();
            return max;
        }

        if(this.utilitySpace.getUtility(lastReceivedBid) > this.thresholdLimit(time)) {
            return new Accept(this.getPartyId(), lastReceivedBid);
        }

        Offer newOffer = new Offer(this.getPartyId(), this.getBid());
        this.myLastBid = newOffer.getBid();
        return newOffer;
    }

    private Bid getBid() {
        Double time = this.timeline.getTime();
        Double cdu = 0D;
        List<Issue> shuffle = new ArrayList<>(this.issues);
        java.util.Collections.shuffle(shuffle);

        HashMap<Integer, Value> bidStore = new HashMap<>();

        for(Issue issue: shuffle) {
            IssueDiscrete issueDiscrete = (IssueDiscrete) issue;
            ArrayList<ValueDiscrete> al = new ArrayList<>();
            ArrayList<Double> oo = new ArrayList<>();
            Double sumoo = 0D;

            Double highestDis = Double.MIN_VALUE;
            ValueDiscrete highestDisValue = issueDiscrete.getValues().get(0);

            for(ValueDiscrete valueDiscrete: issueDiscrete.getValues()) {
                Double dis = this.pref.weights.get(issueDiscrete.getName()).getDisUtility(valueDiscrete);

                // keep tracks of value with highest dis-utiltiy
                if(dis > highestDis) {
                    highestDis = dis;
                    highestDisValue = valueDiscrete;
                }

                if(cdu + dis < this.thresholdLimit(time) - 1) continue;

                Double combined = getCombinedOpponentModelWeight(issueDiscrete, valueDiscrete);
                oo.add(combined);
                al.add(valueDiscrete);

                sumoo += combined;
            }

            if(al.size() == 0) al.add(highestDisValue);

            Integer idx = 0;
            Double rand = Math.random();
            for(int i = 0; i < oo.size(); i++) {
                if(rand < oo.get(i)/sumoo) {
                    idx = i;
                    break;
                }
            }

            ValueDiscrete picked = al.get(idx);

            bidStore.put(issueDiscrete.getNumber(), picked);

            cdu += this.pref.weights.get(issueDiscrete.getName()).getDisUtility(picked);
        }

        return new Bid(this.space.getDomain(), bidStore);
    }

    private Double getCombinedOpponentModelWeight(IssueDiscrete issue, ValueDiscrete value) {
        Integer oo1 = op1.getIssue(issue).getValueCount(value);
        Integer oo2 = op2.getIssue(issue).getValueCount(value);
        return omega * oo1 + (1-omega) * oo2;
    }

    /**
     * This method is called to inform the party that another NegotiationParty chose an Action.
     * @param sender
     * @param action
     */
    @Override
    public void receiveMessage(AgentID sender, Action action) {
        super.receiveMessage(sender, action);

        if (sender != null && (action instanceof Offer || action instanceof Accept)) {

            OpponentModel op = this.getOpponentByName(sender.getName());

            if (action instanceof Offer) { // sender is making an offer

                Offer offer = (Offer) action;

                Bid currentBid = offer.getBid();

                lastReceivedBid = currentBid;

                for (Issue issue: currentBid.getIssues()) {
                    IssueDiscrete isd = (IssueDiscrete) issue;
                    ValueDiscrete vd = (ValueDiscrete) currentBid.getValue(issue.getNumber());
                    op.getIssue(isd).addCount(vd);
                }
            }
        }
    }

    // helper methods
    private Double thresholdLimit(Double time) {
        if(time > 1.0D) return 0.8;
        else return 1.0D - (0.2 * time);
    }

    private OpponentModel getOpponentByName(String name) {

        // check names are set for opponents
        if(op1.getName() == null) {
            op1.setName(name);
        } else if(op2.getName() == null) {
            op2.setName(name);
        }

        return op1.getName().equals(name) ? op1 : op2;
    }

    private Bid getMaxUtilityBid() {
        try {
            return this.utilitySpace.getMaxUtilityBid();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private Boolean hasHigherUtility(Bid firstOffer, Bid secondOffer) {
        return this.utilitySpace.getUtility(firstOffer) >= this.utilitySpace.getUtility(secondOffer);
    }

    /**
     * A human-readable description for this party.
     * @return
     */
    @Override
    public String getDescription() {
        return description;
    }

    @Override
    protected void finalize() throws Throwable {
        System.out.println(op1.toString());
        System.out.println(op2.toString());
        System.out.println(pref.toString());
        super.finalize();
    }
}