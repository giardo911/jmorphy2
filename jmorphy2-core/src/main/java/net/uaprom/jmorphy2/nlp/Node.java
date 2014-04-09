package net.uaprom.jmorphy2.nlp;

import java.util.Comparator;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableList;


public class Node {
    protected final ImmutableList<Node> children;
    protected final ImmutableSet<String> values;
    protected final float score;

    public static final Comparator<Node> comparator =
        new Comparator<Node>() {
            public int compare(Node n1, Node n2) {
                return Float.compare(n1.score, n2.score);
            }
    };

    public Node(ImmutableList<Node> children, ImmutableSet<String> values, float score) {
        this.children = children;
        this.values = values;
        this.score = score;
    }

    public boolean isLeaf() {
        return children == null;
    }

    public ImmutableList<Node> getChildren() {
        return children;
    }

    @Override
    public String toString() {
        return String.format("(%s %s)",
                             Joiner.on(",").join(values),
                             Joiner.on(" ").join(children));
    }

    public static class Top extends Node {
        public Top(ImmutableList<Node> children, float score) {
            super(children, ImmutableSet.of("TOP"), score);
        }

        @Override
        public String toString() {
            return String.format("%s [%s]", super.toString(), score);
        }
    };

    public static abstract class Matcher {
        public abstract boolean match(Node node);
    };
}