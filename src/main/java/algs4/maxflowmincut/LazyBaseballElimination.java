package algs4.maxflowmincut;

import static algs4.maxflowmincut.MaxFlowUtil.choose;
import static algs4.maxflowmincut.MaxFlowUtil.loadTeamData;
import algs4.datastructures.DoublyLinkedList;

import edu.princeton.cs.algs4.FlowEdge;
import edu.princeton.cs.algs4.FlowNetwork;
import edu.princeton.cs.algs4.FordFulkerson;
import edu.princeton.cs.algs4.In;
import edu.princeton.cs.algs4.StdOut;

import java.util.HashMap;
import java.util.Map.Entry;

/**
 * Solving the baseball elimination using the maxflow algorithm.
 * 
 * Given the standings in a sports division at some point during the season, determine which teams 
 * have been mathematically eliminated from winning their division.
 *  
 * The baseball elimination problem.   In the baseball elimination problem, there is a division 
 * consisting of N teams. At some point during the season, team i has w[i] wins, l[i] losses, r[i] 
 * remaining games, and g[i][j] games left to play against team j. A team is mathematically 
 * eliminated if it cannot possibly finish the season in (or tied for) first place. The goal is to 
 * determine exactly which teams are mathematically eliminated. For simplicity, we assume that no 
 * games end in a tie (as is the case in Major League Baseball) and that there are no rainouts 
 * (i.e., every scheduled game is played).
 * 
 * @author Ronny A. Pena
 */
public class LazyBaseballElimination {

  private static final class NetworkSetup {
    private FlowNetwork network;
    private double totalOtherRemaining;

    public NetworkSetup(FlowNetwork network, double totalOtherRemaining) {
      super();
      this.network = network;
      this.totalOtherRemaining = totalOtherRemaining;
    }
  }

  private HashMap<String, Integer> names;
  private Team[] teams;

  // vertices The number of teams.
  private int vertices;
  private int requiredNetworkVertices;
  private int source;
  private int sink;

  /**
   * Create a baseball division from given filename in format specified below.
   * @param filename The file input data.
   */
  public LazyBaseballElimination(String filename) {
    if (filename == null || filename.length() == 0) {
      throw new IllegalArgumentException();
    }
    In inputFile = new In(filename);
    int n = Integer.parseInt(inputFile.readLine());
    vertices = n;
    requiredNetworkVertices = choose(vertices - 1, 2) + vertices + 2;
    source = vertices;
    sink = vertices + 1;
    names = new HashMap<>();
    loadTeamData(inputFile, names, teams);
  }

  /**
   * Builds the flow network.
   */
  private NetworkSetup buildNetwork(int team) {
    int gameVertex = vertices + 2;
    double totalOtherRemaining = 0;
    FlowNetwork network = new FlowNetwork(requiredNetworkVertices);
    for (int team1 = 0; team1 < vertices; team1++) {
      if (team1 != team) {
        for (int team2 = team1 + 1; team2 < vertices; team2++) {
          if (team1 != team2 && team2 != team) {
            int reminder = addSourceEdges(network, gameVertex, team1, team2);
            totalOtherRemaining += reminder;
            gameVertex++;
          }
        }
        addSinkEdges(network, team, team1);
      }
    }
    return new NetworkSetup(network, totalOtherRemaining);
  }

  private int addSourceEdges(FlowNetwork network, int gameVertex, int team1, int team2) {
    int reminder = teams[team1].remainingPerTeam[team2];
    network.addEdge(new FlowEdge(source, gameVertex, reminder));
    network.addEdge(new FlowEdge(gameVertex, team1, Double.POSITIVE_INFINITY));
    network.addEdge(new FlowEdge(gameVertex, team2, Double.POSITIVE_INFINITY));
    return reminder;
  }

  private void addSinkEdges(FlowNetwork network, int mainTeam, int otherTeam) {
    int avaliableToWin = teams[mainTeam].wins + teams[mainTeam].remaining - teams[otherTeam].wins;
    network.addEdge(new FlowEdge(otherTeam, sink, avaliableToWin));
  }

  private DoublyLinkedList<String> computeElimination(int team) {
    DoublyLinkedList<String> eliminationList = trivialElimination(team);
    if (eliminationList != null) {
      return eliminationList;
    }
    // Computes the max flow (mathematical eliminations) for team.
    NetworkSetup setup = buildNetwork(team);
    FordFulkerson maxflow = new FordFulkerson(setup.network, source, sink);
    if (maxflow.value() < setup.totalOtherRemaining) {
      eliminationList = cutElimination(maxflow, team);
      return eliminationList;
    }
    return null;
  }

  private DoublyLinkedList<String> trivialElimination(int team) {
    DoublyLinkedList<String> eliminationNames = null;
    for (Entry<String, Integer> entry : names.entrySet()) {
      int otherTeam = entry.getValue();
      if (otherTeam != team) {
        int avaliableToWin = teams[team].wins + teams[team].remaining - teams[otherTeam].wins;
        if (avaliableToWin < 0) {
          if (eliminationNames == null) {
            eliminationNames = new DoublyLinkedList<>();
          }
          eliminationNames.add(entry.getKey());
        }
      }
    }
    return eliminationNames;
  }


  private DoublyLinkedList<String> cutElimination(FordFulkerson maxflow, int team) {
    DoublyLinkedList<String> eliminationNames = null;
    for (Entry<String, Integer> entry : names.entrySet()) {
      int otherTeam = entry.getValue();
      if (otherTeam != team && maxflow.inCut(otherTeam)) { // part of the cut
        if (eliminationNames == null) {
          eliminationNames = new DoublyLinkedList<>();
        }
        eliminationNames.add(entry.getKey());
      }
    }
    return eliminationNames;
  }


  private void lazyComputeElimination(int teamIndex) {
    if (!teams[teamIndex].calculated) {
      teams[teamIndex].eliminatedBy = computeElimination(teamIndex);
      teams[teamIndex].calculated = true;
    }
  }

  /**
   * @param team The selected team.
   * @return is given team eliminated?
   */
  public boolean isEliminated(String team) {
    if (team == null || !names.containsKey(team)) {
      throw new IllegalArgumentException();
    }

    int teamIndex = names.get(team);
    lazyComputeElimination(teamIndex);
    return teams[teamIndex].eliminatedBy != null;
  }

  /**
   * isEliminated must be called first, otherwise, this will always return null.
   * 
   * @param team The selected team.
   * @return subset R of teams that eliminates given team; null if not eliminated
   */
  public Iterable<String> certificateOfElimination(String team) {
    if (team == null || !names.containsKey(team)) {
      throw new IllegalArgumentException();
    }
    Integer teamIndex = names.get(team);
    lazyComputeElimination(teamIndex);
    return teams[teamIndex].eliminatedBy;
  }


  /**
   * @return The number of teams.
   */
  public int numberOfTeams() {
    return names.size();
  }

  /**
   * @return All the teams.
   */
  public Iterable<String> teams() {
    return names.keySet();
  }

  /**
   * @param team The selected team.
   * @return The number of wins for given team.
   */
  public int wins(String team) {
    if (team == null || team.length() == 0 || !names.containsKey(team)) {
      throw new IllegalArgumentException();
    }
    return teams[names.get(team)].wins;
  }

  /**
   * @param team The selected team.
   * @return The number of losses for given team.
   */
  public int losses(String team) {
    if (team == null || team.length() == 0 || !names.containsKey(team)) {
      throw new IllegalArgumentException();
    }
    return teams[names.get(team)].loses;
  }

  /**
   * @param team The selected team.
   * @return number of remaining games for given team.
   */
  public int remaining(String team) {
    if (team == null || team.length() == 0 || !names.containsKey(team)) {
      throw new IllegalArgumentException();
    }
    return teams[names.get(team)].remaining;
  }

  /**
   * @param team1 The selected team.
   * @param team2 The other team.
   * @return number of remaining games between team1 and team2.
   */
  public int against(String team1, String team2) {
    if (team1 == null || team1.length() == 0 || !names.containsKey(team1)) {
      throw new IllegalArgumentException();
    }
    if (team2 == null || team2.length() == 0 || !names.containsKey(team2)) {
      throw new IllegalArgumentException();
    }
    return teams[names.get(team1)].remainingPerTeam[names.get(team2)];
  }

  /**
   * Main driver.
   */
  public static void main(String[] args) {
    LazyBaseballElimination division = new LazyBaseballElimination(args[0]);
    for (String team : division.teams()) {
      if (division.isEliminated(team)) {
        StdOut.print(team + " is eliminated by the subset R = { ");
        for (String t : division.certificateOfElimination(team)) {
          StdOut.print(t + " ");
        }
        StdOut.println("}");
      } else {
        StdOut.println(team + " is not eliminated");
      }
    }
  }
}
