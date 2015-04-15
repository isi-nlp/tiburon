package edu.isi.tiburon;

import java.util.Comparator;
import java.util.PriorityQueue;


class LeftTransducerRuleElements {


	public TransducerLeftTree lhs;
	public Item rhs;
	public double weight;
	public Semiring semiring;
	public LeftTransducerRuleElements(TransducerLeftTree tlt, Item trt, double wgt, Semiring s) {
		lhs=tlt;
		rhs=trt;
		weight=wgt;
		semiring=s;
	}
	public String toString() {
		return lhs+":"+rhs+":"+weight;
	}
	public static final int DEFAULT_HEAP=50;
	
	private static class LeftTransducerRuleElementsComp implements Comparator<LeftTransducerRuleElements> {

		// reversed for top-level beaming!
		// (if we're doing bottom-level beaming, it should be in normal order, i.e. better => 1
		public int compare(LeftTransducerRuleElements o1, LeftTransducerRuleElements o2) {
			if (o1.semiring.better(o1.weight, o2.weight))
				return -1;
			else if (o1.semiring.better(o2.weight, o1.weight))
				return 1;
			else
				return 0;
		}
	}
	public static PriorityQueue<LeftTransducerRuleElements> getHeap(int beam) {
		return new PriorityQueue<LeftTransducerRuleElements>(beam>0 ? beam : DEFAULT_HEAP, new LeftTransducerRuleElementsComp());

	}

}
