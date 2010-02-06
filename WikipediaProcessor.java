import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Comparator;

/**
 * @author tjungen
 */
public class WikipediaProcessor {
  public static void main(String[] args) throws Exception {
    WikipediaProcessor processor = new WikipediaProcessor();
    Reader reader = new FileReader(args[0]);
    processor.parse(reader);
    reader.close();
  }

  public static final int BUFFER_SIZE = 4096;

  public WikipediaProcessor() {
    tokenList.addAll(tokenMap.keySet());
    Collections.sort(tokenList, new Comparator<String>() {
      @Override public int compare(String a, String b) { return a.length() - b.length(); }
    });
    Collections.reverse(tokenList);
  }

  protected boolean eof = false;
  protected Queue<Token> tokenQueue = new LinkedList<Token>();
  protected CharBuffer buffer = CharBuffer.allocate(BUFFER_SIZE);

  protected List<String> tokenList = new ArrayList<String>();
  protected Map<String, TokenType> tokenMap = new HashMap<String, TokenType>() {{
    put("''", TokenType.ITALICS);
    put("'''", TokenType.BOLD);
    put("'''''", TokenType.BOLDITALICS);
    put("{{", TokenType.TEMPL_OPEN);
    put("}}", TokenType.TEMPL_CLOSE);
    put("{{{", TokenType.TEMPLARG_OPEN);
    put("}}}", TokenType.TEMPLARG_CLOSE);
    put("|", TokenType.PIPE);
    put("[[", TokenType.LINK_OPEN);
    put("]]", TokenType.LINK_CLOSE);
    put("[", TokenType.WEBLINK_OPEN);
    put("]", TokenType.WEBLINK_CLOSE);
    put("<", TokenType.ANGLE_OPEN);
    put("</", TokenType.ANGLE_OPENSLASH);
    put(">", TokenType.ANGLE_CLOSE);
    put("=", TokenType.EQUALS);
    put("\"", TokenType.DBLQUOTE);
  }};

  public void parse(Reader reader) throws IOException {
    tokenQueue.clear();
    buffer.clear();
    eof = false;

    if (!reader.markSupported()) {
      reader = new BufferedReader(reader);
    }

    Node root = new Node();
    root.type = NodeType.ROOT;
    Node current = root;

    while (!eof || !tokenQueue.isEmpty()) {
      if (tokenQueue.isEmpty()) {
        nextToken(reader);
      }

      if (!tokenQueue.isEmpty()) {
        Token token = tokenQueue.remove();

        if (token.type == TokenType.BOLD) {
          current = isOpen(current, NodeType.BOLD) ?
                    current.parent : addSubNode(current, NodeType.BOLD);
        }
        else if (token.type == TokenType.ITALICS) {
          current = isOpen(current, NodeType.ITALICS) ?
                    current.parent : addSubNode(current, NodeType.ITALICS);
        }
        else if (token.type == TokenType.LINK_OPEN) {
          current = addSubNode(current, NodeType.LINK);
        }
        else if (token.type == TokenType.LINK_CLOSE) {
          if (isOpen(current, NodeType.LINK)) {
            current = current.parent;
          }
          else {
            System.err.println("Mismatched link");
          }
        }
        else if (token.type == TokenType.WEBLINK_OPEN) {
          current = addSubNode(current, NodeType.WEBLINK);
        }
        else if (token.type == TokenType.WEBLINK_CLOSE) {
          if (isOpen(current, NodeType.WEBLINK)) {
            current = current.parent;
          }
          else {
            System.err.println("Mismatched weblink");
          }
        }
        else {
          addNode(current, token.str);
        }
      }
    }

    printNode(root, 0);

    System.out.println();
  }

  protected boolean isOpen(Node node, NodeType type) {
    while (node != null && node.type != NodeType.ROOT) {
      if (node.type == type) {
        break;
      }

      node = node.parent;
    }

    return node != null && node.type != NodeType.ROOT;
  }

  protected Node addSubNode(Node current, NodeType type) {
    Node node = new Node();
    node.type = type;
    node.parent = current;
    current.children.add(node);
    return node;
  }

  protected Node addNode(Node current, String str) {
    Node node = new Node();
    node.parent = current;
    node.str = str;
    current.children.add(node);
    return node;
  }

  protected void printNode(Node node, int depth) {
    if (node.type == NodeType.DEFAULT) {
      System.out.println(fillStr(' ', depth * 2) + node.str);
    }
    else {
      System.out.println(fillStr(' ', depth * 2) + node.type);

      for (Node child : node.children) {
        printNode(child, depth + 1);
      }
    }
  }

  protected String fillStr(char c, int n) {
    char[] data = new char[n];
    java.util.Arrays.fill(data, c);
    return String.valueOf(data);
  }

  protected void nextToken(Reader reader) throws IOException {
    int c = 0;

    while (c >= 0) {
      for (String markupToken : tokenList) {
        if (readerContains(reader, markupToken)) {
          flushBuffer();
          reader.skip(markupToken.length());
          tokenQueue.add(new Token(tokenMap.get(markupToken)));
          return;
        }
      }

      if (eof) return;

      c = reader.read();

      if (c < 0) {
        eof = true;
        return;
      }

      char ch = (char) c;

      if (Character.isWhitespace(ch)) {
        if (ch == '\n') {
          flushBuffer();
          tokenQueue.add(new Token(TokenType.NEWLINE));
          return;
        }
        else if (flushBuffer()) {
          return;
        }
      }
      else {
        buffer.append(ch);
      }
    }
    
    flushBuffer();
  }

  protected boolean flushBuffer() {
    if (buffer.position() > 0) {
      tokenQueue.add(new Token(buffer.duplicate().flip().toString()));
      buffer.clear();
      return true;
    }

    return false;
  }

  protected boolean readerContains(Reader reader, String str) throws IOException {
    if (eof) return false;
    reader.mark(BUFFER_SIZE);

    for (int x = 0; x < str.length(); ++x) {
      int c = reader.read();

      if (c < 0) {
        eof = true;
        return false;
      }

      if (str.charAt(x) != (char) c) {
        reader.reset();
        return false;
      }
    }

    reader.reset();
    return true;
  }

  protected static class Token {
    public String str;
    public TokenType type = TokenType.DEFAULT;

    public Token(TokenType type) {
      this.type = type;
    }

    public Token(String str) {
      this.str = str;
    }

    @Override public String toString() {
      if (type == TokenType.DEFAULT) return str;
      else return ":" + type.name();
    }
  }

  protected static enum TokenType {
    DEFAULT,
    NEWLINE,
    BOLD,
    ITALICS,
    LINK_OPEN,
    LINK_CLOSE,
    WEBLINK_OPEN,
    WEBLINK_CLOSE,
    ANGLE_OPEN,
    ANGLE_OPENSLASH,
    ANGLE_CLOSE,
    EQUALS,
    DBLQUOTE,
    BOLDITALICS,
    TEMPL_OPEN,
    TEMPL_CLOSE,
    TEMPLARG_OPEN,
    TEMPLARG_CLOSE,
    PIPE
  }

  protected static class Node {
    public Node parent;
    public NodeType type = NodeType.DEFAULT;
    public List<Node> children = new ArrayList<Node>();
    public String str;
  }

  protected static enum NodeType {
    ROOT,
    DEFAULT,
    BOLD,
    ITALICS,
    LINK,
    WEBLINK
    // more to come
  }
}
