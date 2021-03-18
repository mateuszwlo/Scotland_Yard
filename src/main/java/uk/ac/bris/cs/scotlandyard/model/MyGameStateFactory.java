package uk.ac.bris.cs.scotlandyard.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

import com.google.common.collect.Iterables;
import org.checkerframework.checker.nullness.Opt;
import uk.ac.bris.cs.scotlandyard.model.Board.GameState;
import uk.ac.bris.cs.scotlandyard.model.Move.*;
import uk.ac.bris.cs.scotlandyard.model.Piece.*;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYard.*;

public final class MyGameStateFactory implements Factory<GameState> {
    @Nonnull
    @Override
    public GameState build(GameSetup setup, Player mrX, ImmutableList<Player> detectives) {
        return new MyGameState(setup, ImmutableSet.of(mrX.piece()), ImmutableList.of(), mrX, detectives);
    }

    private static ImmutableSet<Move> makeMoves(
            final GameSetup setup,
            final Player mrX,
            final List<Player> detectives,
            final ImmutableSet<Piece> remaining,
            final ImmutableList<LogEntry> log
            ) {
        final ArrayList<Move> moves = new ArrayList<>();

        if (remaining.contains(mrX.piece())) {
            final ImmutableSet<SingleMove> mrXSingleMoves = makeSingleMoves(setup, detectives, mrX, mrX.location());
            moves.addAll(mrXSingleMoves);
            for (SingleMove m : mrXSingleMoves) {
                if (mrX.has(Ticket.DOUBLE) && setup.rounds.size() - log.size() >= 2) {
                    final ImmutableSet<SingleMove> doubleMoves = makeSingleMoves(setup, detectives, mrX, m.destination);
                    for (SingleMove m2: doubleMoves) {
                        final Ticket ticket1 = Iterables.getFirst(m.tickets(), null);
                        final Ticket ticket2 = Iterables.getFirst(m2.tickets(), null);
                        if (ticket1 != ticket2 || mrX.hasAtLeast(ticket1, 2)) {
                            moves.add(new DoubleMove(mrX.piece(), mrX.location(), ticket1, m.destination, ticket2, m2.destination));
                        }
                    }
                }
            }
        }
        else {
            for (Player d : detectives) {
                if (!remaining.contains(d.piece())) continue;
                final ImmutableSet<SingleMove> detectiveMoves = makeSingleMoves(setup, detectives, d, d.location());
                moves.addAll(detectiveMoves);
            }
        }

        return ImmutableSet.copyOf(moves);
    }

    private static ImmutableSet<Piece> playersToPieceSet(List<Player> detectives) {
        final List<Piece> detectivesPieces = detectives.stream()
                .map(Player::piece)
                .collect(Collectors.toList());
        return ImmutableSet.copyOf(detectivesPieces);
    }

    private static ImmutableSet<SingleMove> makeSingleMoves(
            GameSetup setup,
            List<Player> detectives,
            Player player,
            int source){
        final var singleMoves = new ArrayList<SingleMove>();
        for (int destination : setup.graph.adjacentNodes(source)) {
            //  If the destination is occupied by a detective, skip
            boolean isOccupied = false;
            for (Player p : detectives) {
                if (p.location() == destination) {
                    isOccupied = true;
                    break;
                }
            }
            if (isOccupied) continue;

            //  If the player has a ticket that allows them to go to destination,
            //  add the move to the array
            for (Transport t : setup.graph.edgeValueOrDefault(source, destination, ImmutableSet.of())) {
                final Ticket ticket = t.requiredTicket();
                if (player.has(ticket)) {
                    singleMoves.add(new SingleMove(player.piece(), source, ticket, destination));
                }
            }
            if (player.isMrX() && player.has(Ticket.SECRET)) {
                singleMoves.add(new SingleMove(player.piece(), source, Ticket.SECRET, destination));
            }
        }

        return ImmutableSet.copyOf(singleMoves);
    }

    ImmutableSet<Piece> determineWinner(MyGameState gameState){
        for (Player d : gameState.detectives) {
            if (d.location() == gameState.mrX.location()) {
                return playersToPieceSet(gameState.detectives);
            }
        }

        boolean detectivesHaveTickets = false;
        for (Player d : gameState.detectives) {
            boolean dHasAnyTickets = false;
            for (Ticket t : d.tickets().keySet()) {
                if (d.has(t)) dHasAnyTickets = true;
            }
            if (dHasAnyTickets) {
                detectivesHaveTickets = true;
                break;
            }
        }

        if (!detectivesHaveTickets) return ImmutableSet.of(gameState.mrX.piece());

        // if there have been 24 rounds -> mrX wins
        if (gameState.setup.rounds.size() == gameState.log.size() && gameState.remaining.contains(gameState.mrX.piece())) {
            return ImmutableSet.of(gameState.mrX.piece());
        }
        // if it's the turn opf the detectives and the detectives have no moves left -> mrX wins
        if (gameState.remaining.contains(gameState.mrX.piece())) {
            if (gameState.moves.isEmpty()) return playersToPieceSet(gameState.detectives);
        }
        else {
            boolean detectivesHaveMoves = false;
            for (Move m : gameState.moves) {
                if (m.commencedBy().isDetective()) {
                    detectivesHaveMoves = true;
                    break;
                }
            }
            if (!detectivesHaveMoves) return ImmutableSet.of(gameState.mrX.piece());
        }

        return ImmutableSet.of();
    }

    private final class MyGameState implements GameState {
        private GameSetup setup;
        private ImmutableSet<Piece> remaining;
        private ImmutableList<LogEntry> log;
        private Player mrX;
        private List<Player> detectives;
        private ImmutableList<Player> everyone;
        private ImmutableSet<Move> moves;
        private ImmutableSet<Piece> winner;

        private MyGameState(
                final GameSetup setup,
                final ImmutableSet<Piece> remaining,
                final ImmutableList<LogEntry> log,
                final Player mrX,
                final List<Player> detectives) {
            checkParameters(setup, mrX, detectives);

            //Build remaining and everyone lists
            ImmutableList.Builder<Player> everyoneBuilder = ImmutableList.builder();

            everyoneBuilder.add(mrX);
            for (Player p : detectives) everyoneBuilder.add(p);

            this.setup = setup;
            this.remaining = remaining;
            this.log = log;
            this.mrX = mrX;
            this.detectives = detectives;
            this.everyone = everyoneBuilder.build();
            this.moves = makeMoves(setup, mrX, detectives, remaining, log);
            this.winner = determineWinner(this);
            if(!this.winner.isEmpty()) this.moves = ImmutableSet.of();
        }

        void checkParameters(GameSetup setup, Player mrX, List<Player> detectives) {
            if (setup.rounds.isEmpty()) throw new IllegalArgumentException("Rounds is empty");

            if (setup.graph.nodes().isEmpty()) throw new IllegalArgumentException("Graph is empty");

            if (mrX == null) throw new NullPointerException("MrX is empty!");

            if (!mrX.piece().webColour().equals("#000"))
                throw new IllegalArgumentException("MrX must be a black piece");

            if (detectives.isEmpty()) throw new IllegalArgumentException("Detectives is empty");

            for (int i = 0; i < detectives.size(); i++){
                Player p = detectives.get(i);
                if (p == null) throw new IllegalArgumentException("One or more detectives are null");

                if (p.isDetective() && p.tickets().get(Ticket.DOUBLE) != 0)
                    throw new IllegalArgumentException("One or more detectives has a double ticket");

                if (p.isDetective() && p.tickets().get(Ticket.SECRET) != 0)
                    throw new IllegalArgumentException("One or more detectives has a secret ticket");

                if (p.isMrX()) throw new IllegalArgumentException("There can only be one MrX");

                for (int j = i + 1; j < detectives.size(); j++){
                    if (p.location() == detectives.get(j).location()) {
                        throw new IllegalArgumentException("Two players have the same location");
                    }
                }
            }
        }

        @Nonnull
        @Override
        public GameSetup getSetup() {
            return setup;
        }

        @Nonnull
        @Override
        public ImmutableSet<Piece> getPlayers() {
            ImmutableSet.Builder<Piece> everyoneBuilder = ImmutableSet.builder();

            for (Player p : everyone) everyoneBuilder.add(p.piece());

            return everyoneBuilder.build();
        }

        @Nonnull
        @Override
        public Optional<Integer> getDetectiveLocation(Detective detective) {
            for(Player p: detectives){
                if(p.piece() == detective) return Optional.of(p.location());
            }
            return Optional.empty();
        }

        @Nonnull
        @Override
        public Optional<TicketBoard> getPlayerTickets(Piece piece) {
            for(Player p: everyone){
                if(p.piece() == piece) return Optional.of(new MyTicketBoard(p.tickets()));
            }
            return Optional.empty();
        }

        @Nonnull
        @Override
        public ImmutableList<LogEntry> getMrXTravelLog() {
            return log;
        }

        @Nonnull
        @Override
        public ImmutableSet<Piece> getWinner() {
            return winner;
        }

        @Nonnull
        @Override
        public ImmutableSet<Move> getAvailableMoves() {
            return moves;
        }

        ImmutableList<LogEntry> addToLog(ImmutableList<LogEntry> log, Ticket ticket, int destination) {
            List<LogEntry> newLog = new ArrayList<>(log);

            if (setup.rounds.get(newLog.size())) newLog.add(LogEntry.reveal(ticket, destination));
            else newLog.add(LogEntry.hidden(ticket));

            return ImmutableList.copyOf(newLog);
        }

        ImmutableList<LogEntry> singleMoveFunction(ImmutableList<LogEntry> log, Move move) {
            SingleMove sm = (SingleMove) move;
            return addToLog(log, Iterables.getFirst(move.tickets(), null), sm.destination);
        }

        ImmutableList<LogEntry> doubleMoveFunction(ImmutableList<LogEntry> log, Move move) {
            DoubleMove dm = (DoubleMove) move;
            final ImmutableList<LogEntry> logWithFirstMove = addToLog(log, dm.ticket1, dm.destination1);
            return addToLog(logWithFirstMove, dm.ticket2, dm.destination2);
        }

        @Override
        public GameState advance(Move move) {
            if (!moves.contains(move)) throw new IllegalArgumentException("Illegal move: " + move);

            List<Piece> newRemaining = new ArrayList<>(remaining);
            ImmutableList<LogEntry> newLog = log;
            Player newMrX = mrX;
            List<Player> newDetectives = new ArrayList<>(detectives);
            newRemaining.remove(move.commencedBy());

            if (move.commencedBy().isMrX()) {
                final int destination = move.visit(new FunctionalVisitor<Integer>(
                        m -> ((SingleMove) m).destination,
                        m -> ((DoubleMove) m).destination2
                ));
                newMrX = newMrX.at(destination);
                newMrX = newMrX.use(move.tickets());
                newLog = move.visit(new FunctionalVisitor<ImmutableList<LogEntry>>(
                        m -> singleMoveFunction(log, move),
                        m -> doubleMoveFunction(log, move)
                ));

                for (Player d : detectives) {
                    boolean dHasAnyTickets = false;
                    for (Ticket t : d.tickets().keySet()) {
                        if (d.has(t)) dHasAnyTickets = true;
                    }
                    if (dHasAnyTickets) newRemaining.add(d.piece());
                }
            }
            else {
                for (int i = 0; i < newDetectives.size(); i++) {
                    Player d = newDetectives.get(i);
                    if (d.piece() == move.commencedBy()) {
                        SingleMove sm = (SingleMove) move;
                        newDetectives.set(i, d.at(sm.destination));
                        d = newDetectives.get(i);
                        newDetectives.set(i, d.use(move.tickets()));
                        newMrX = newMrX.give(move.tickets());
                    }
                }

                if(newRemaining.isEmpty()) newRemaining.add(newMrX.piece());
            }

            return new MyGameState(setup, ImmutableSet.copyOf(newRemaining), newLog, newMrX, ImmutableList.copyOf(newDetectives));
        }
    }

    private class MyTicketBoard implements Board.TicketBoard {

        private final ImmutableMap<Ticket, Integer> tickets;

        public MyTicketBoard(ImmutableMap<Ticket, Integer> tickets) {
            this.tickets = tickets;
        }

        @Override
        public int getCount(@Nonnull Ticket ticket) {
            return tickets.get(ticket);
        }
    }
}