package edu.isi.tiburon;

import java.util.Comparator;
import java.util.PriorityQueue;

class RightTransducerRuleElements {


	public TreeItem lhs;
	public TransducerRightSide rhs;
	public double weight;
	public Semiring semiring;
	public RightTransducerRuleElements(TreeItem tlt, TransducerRightSide trt, double wgt, Semiring s) {
		lhs=tlt;
		rhs=trt;
		weight=wgt;
		semiring=s;
	}
	public String toString() {
		return lhs+":"+rhs+":"+weight;
	}
	public static final int DEFAULT_HEAP=50;
	
	
	// reversed for top-level beaming!
	// (if we're doing bottom-level beaming, it should be in normal order, i.e. better => 1
	private static class RightTransducerRuleElementsComp implements Comparator<RightTransducerRuleElements> {
		public int compare(RightTransducerRuleElements o1, RightTransducerRuleElements o2) {
			if (o1.semiring.better(o1.weight, o2.weight))
				return -1;
			else if (o1.semiring.better(o2.weight, o1.weight))
				return 1;
			else
				return 0;
		}
	}
	public static PriorityQueue<RightTransducerRuleElements> getHeap(int beam) {
		return new PriorityQueue<RightTransducerRuleElements>(beam>0 ? beam : DEFAULT_HEAP, new RightTransducerRuleElementsComp());

	}

}
