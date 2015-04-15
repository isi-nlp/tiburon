/*
 * @(#)ASCIITreeGraphics.java	1.0 1/18/96 Michael John Radwin
 *
 *  Copyright (c) 1996  Michael J. Radwin.
 *  All rights reserved.
 * 
 *  Redistribution and use in source and binary forms, with or
 *  without modification, are permitted provided that the following
 *  conditions are met:
 * 
 *   * Redistributions of source code must retain the above
 *     copyright notice, this list of conditions and the following
 *     disclaimer.
 * 
 *   * Redistributions in binary form must reproduce the above
 *     copyright notice, this list of conditions and the following
 *     disclaimer in the documentation and/or other materials
 *     provided with the distribution.
 * 
 *   * Neither the name of Radwin.org nor the names of its
 *     contributors may be used to endorse or promote products
 *     derived from this software without specific prior written
 *     permission.
 * 
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 *  CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 *  INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 *  MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 *  DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 *  CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 *  SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 *  NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 *  LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 *  HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 *  CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 *  OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 *  SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

package mjr.treegraphics;

/**
 * The graphics driver for an ASCII tree.
 * <p>
 * The TreeGraphics routines work by getting from you a pre-order
 * traversal of your tree (root-left-right), in terms of calls to
 * <code>DrawInternal</code> and <code>DrawLeaf</code>. The exact
 * semantics of these calls are:
 * <pre>
 * ASCIITreeGraphics.DrawInternal(String nodeLabel);
 * ASCIITreeGraphics.DrawLeaf();
 * </pre>
 *
 * This code was ported by
 * <A HREF="http://www.radwin.org/michael/">mjr</A>
 * from the pascal source written by
 * <A HREF="http://www.cs.brown.edu/people/bmc/">bmc</A>.
 * <p>
 * @see mjr.treegraphics.TreeGraphics
 * @version 1.0 1/18/96
 * @author <A HREF="http://www.radwin.org/michael/">Michael John Radwin</A>
 * @author <A HREF="http://www.cs.brown.edu/people/bmc/">Bryan Cantrill</A>
 */
public
class ASCIITreeGraphics extends TreeGraphics {
    public static final int TAG_DIF_X   =    74;
    public static final int TAG_BASE_X  =    1;
    public static final int TAG_DIF_Y   =    4;
    public static final int TAG_BASE_Y  =    2;
    public static final int TAG_MAX_X   =    155;
    public static final int TAG_MAX_Y   =    50;

    private static char Tree[][] = new char[TAG_MAX_X][TAG_MAX_Y];
    private static int iMaxLevel_ = 0;
    private static boolean fDone_ = false;
    private static Tpnt ppntTop_, ppntFirst_, ppntLast_;

    /**
     * Initializes the graphics routines with an empty tree
     */
    public ASCIITreeGraphics()
    {
	super();
	ppntTop_ = null;
	ppntFirst_ = null;
	Clear();
    }


    /**
     * Draws an internal node to standard output.
     *
     * @param nodeLabel the label for the internal node
     */
    public void DrawInternal(String nodeLabel, String color)
    {
	DrawInternal(nodeLabel);
    }
    
    /**
     * Draws an internal node to standard output.
     *
     * @param nodeLabel the label for the internal node
     */
    public void DrawInternal(String nodeLabel)
    {
	Tpnt ppntNew;
	
	if (fDone_)
	    Clear();

	ppntNew = NewPoint(nodeLabel);

	AddNode();

	ppntNew.ppntNext = ppntTop_;
	ppntTop_ = ppntNew;
	if (ppntTop_.ppntNext == null)
	    fDone_ = true;
    }


    /**
     * Draws a leaf to standard output.
     */
    public void DrawLeaf(String color)
    {
	DrawLeaf();
    }
    
    /**
     * Draws a leaf to standard output.
     */
    public void DrawLeaf()
    {
	Tpnt ppntNew;
	int i, j;

	if (fDone_)
	    Clear();

	ppntNew = NewPoint("[_]");
	AddNode();

	System.out.print('\n');
	if (ppntTop_.ppntNext == null) {
	    for (i = 0; i < (iMaxLevel_ + 1) * TAG_DIF_Y; i++) {
		for (j = 0; j < TAG_MAX_X; j++)
		    System.out.print(Tree[j][i]);
		System.out.print('\n');
	    }
	    fDone_ = true;
	}

	System.out.print('\n');
	System.out.flush();

	if (ppntFirst_ == null)
	    ppntFirst_ = ppntNew;
	else
	    ppntLast_.ppntNext = ppntNew;
	ppntLast_ = ppntNew;
    }


    /**
     * empties the tree of all nodes
     */
    private void Clear()
    {
	Tpnt ppntCurrent, ppntNext;
	int i, j;

	iMaxLevel_ = 0;
	ppntCurrent = ppntFirst_;

	fDone_ = false;

	while (ppntCurrent != null) {
	    ppntNext = ppntCurrent.ppntNext;
	    ppntCurrent = null;  // free(ppntCurrent);
	    ppntCurrent = ppntNext;
	}

	ppntFirst_ = null;
	ppntLast_ = null;

	while (ppntTop_ != null) {
	    ppntCurrent = ppntTop_;
	    ppntTop_ = ppntTop_.ppntNext;
	    ppntCurrent = null; // free(ppntCurrent);
	}

	for (i = 0; i < TAG_MAX_Y; i++) {
	    for (j = 0; j < TAG_MAX_X; j++)
		Tree[j][i] = ' ';
	}

	ppntTop_ = new Tpnt();
	ppntTop_.iXCoord = TAG_BASE_X + TAG_DIF_X * 2;
	ppntTop_.iLevel = -1;
	ppntTop_.ppntNext = null;
	ppntTop_.chlNext = Tpnt.Left;
	ppntTop_.iVertexID = 0;
	ppntTop_.iEdgeID = 0;
    }



    private void AddNode()
    {
	Tpnt ppntTemp;

	if (ppntTop_.chlNext != Tpnt.Right) {
	    ppntTop_.chlNext = Tpnt.Right;
	    return;
	}

	ppntTemp = ppntTop_;
	ppntTop_ = ppntTop_.ppntNext;
	ppntTemp.ppntNext = null;

	if (ppntFirst_ == null)
	    ppntFirst_ = ppntTemp;
	else
	    ppntLast_.ppntNext = ppntTemp;

	ppntLast_ = ppntTemp;
    }


    private Tpnt NewPoint(String sLabel)
    {
	Tpnt ppntNew;
	int iDifX, i, l;

	ppntNew = new Tpnt();

	ppntNew.ppntNext = null;
	ppntNew.chlNext = Tpnt.Left;
	ppntNew.iLevel = ppntTop_.iLevel + 1;

	if (ppntNew.iLevel > iMaxLevel_)
	    iMaxLevel_ = ppntNew.iLevel;

	ppntNew.iYCoord = TAG_BASE_Y + ppntNew.iLevel * TAG_DIF_Y;

	iDifX = TAG_DIF_X / (1 << ppntNew.iLevel);

	if (ppntTop_.chlNext == Tpnt.Left)
	    ppntNew.iXCoord = ppntTop_.iXCoord - iDifX;
	else
	    ppntNew.iXCoord = ppntTop_.iXCoord + iDifX;

	l = ppntNew.iXCoord;
	l -= (sLabel.length() - 1) / 2;

	for (i = 1; i <= sLabel.length(); i++)
	    Tree[l + i - 2][ppntNew.iYCoord - 2] = sLabel.charAt(i - 1);

	if (ppntTop_.iLevel < 0)
	    return ppntNew;

	Tree[ppntTop_.iXCoord - 1][ppntTop_.iYCoord - 1] = '+';
	if (ppntNew.iXCoord > ppntTop_.iXCoord)
	    for (i = ppntNew.iXCoord - 1; i >= ppntTop_.iXCoord; i--)
		Tree[i][ppntTop_.iYCoord - 1] = '-';
	else
	    for (i = ppntNew.iXCoord - 1; i <= ppntTop_.iXCoord - 2; i++)
		Tree[i][ppntTop_.iYCoord - 1] = '-';

	Tree[ppntNew.iXCoord - 1][ppntTop_.iYCoord - 1] = '.';
	for (i = ppntTop_.iYCoord; i <= ppntNew.iYCoord - 3; i++)
	    Tree[ppntNew.iXCoord - 1][i] = '|';
	
	return ppntNew;
    }

    static public void main(String args[])
    {
	ASCIITreeGraphics tree = new ASCIITreeGraphics();
    
	tree.DrawInternal("39");
	tree.DrawInternal("25");
	tree.DrawInternal("19");
	tree.DrawInternal("13");
	tree.DrawLeaf();
	tree.DrawLeaf();
	tree.DrawLeaf();
	tree.DrawInternal("35");
	tree.DrawLeaf();
	tree.DrawInternal("38");
	tree.DrawLeaf();
	tree.DrawLeaf();
	tree.DrawInternal("45");
	tree.DrawLeaf();
	tree.DrawInternal("51");
	tree.DrawLeaf();
	tree.DrawLeaf();
    }
}


/**
 * pnt is Hungarian for Point record type
 *
 * @version 1.0 2/3/96
 * @author <A HREF="http://www.radwin.org/michael/">Michael John Radwin</A>
 */
final class Tpnt {
    public int iXCoord, iYCoord, iLevel, iValue, iVertexID, iEdgeID;
    public int chlNext = Left;
    public Tpnt ppntNext;

    /**
     * This was formerly a member of Tchl, but Java doesn't support
     * enumerated types.
     */
    static public final int Left = 0;

    /**
     * This was formerly a member of Tchl, but Java doesn't support
     * enumerated types.
     */
    static public final int Right = 1;
}
