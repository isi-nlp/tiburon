package edu.isi.tiburon;

import java.util.Vector;

// returns templates given lhs and full rules given int and template
public interface IPTemplateAccess {

	public Iterable<Vector<Integer>> getTemplates(int s);
	public Iterable<IPRule> getTemplates(int s, Vector<Integer> v);
}
