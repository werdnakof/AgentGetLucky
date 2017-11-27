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

import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private int roundCount = 0;

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

            OpponentModel.IssueStore is1 = op1.getIssue(issueDiscrete);
            OpponentModel.IssueStore is2 = op2.getIssue(issueDiscrete);

//            System.out.println("IssueName " + issueDiscrete.getName());

            for (ValueDiscrete valueDiscrete : issueDiscrete.getValues()) {

//                System.out.println("ValueName " + valueDiscrete.getValue());
//                System.out.println("Evaluation(getValue): " + evaluatorDiscrete.getValue(valueDiscrete));

                is1.addValue(valueDiscrete);
                is2.addValue(valueDiscrete);

//                try {
//                    System.out.println("Evaluation(getEvaluation): " + evaluatorDiscrete.getEvaluation(valueDiscrete));
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
            }
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
        super.finalize();
    }
}