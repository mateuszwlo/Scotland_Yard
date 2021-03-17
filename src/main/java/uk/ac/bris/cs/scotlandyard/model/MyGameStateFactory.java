package uk.ac.bris.cs.scotlandyard.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.util.*;
import javax.annotation.Nonnull;

import org.checkerframework.checker.nullness.Opt;
import uk.ac.bris.cs.scotlandyard.model.Board.GameState;
import uk.ac.bris.cs.scotlandyard.model.Move.*;
import uk.ac.bris.cs.scotlandyard.model.Piece.*;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYard.*;

public final class MyGameStateFactory implements Factory<GameState> {
    @Nonnull
    @Override
    public GameState build(GameSetup setup, Player mrX, ImmutableList<Player> detectives) {
        return new MyGameState(setup, ImmutableSet.of(MrX.MRX), ImmutableList.of(), mrX, detectives);
    }

    private static ImmutableSet<Move> makeMoves(
            final GameSetup setup,
            final Player mrX,
            final List<Player> detectives
            ) {
        final ArrayList<Move> moves = new ArrayList<Move>();

        for (Player d : detectives) {
            final ImmutableSet<SingleMove> detectiveMoves = makeSingleMoves(setup, detectives, d, d.location());
            for (Move m : detectiveMoves) moves.add(m);
        }

        // TODO implement mrX moves

        return ImmutableSet.copyOf(moves);
    }

    private static ImmutableSet<SingleMove> makeSingleMoves(
            GameSetup setup,
            List<Player> detectives,
            Player player,
            int source){
        final var singleMoves = new ArrayList<SingleMove>();
        for(int destination : setup.graph.adjacentNodes(source)) {
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
            for(Transport t : setup.graph.edgeValueOrDefault(source,destination,ImmutableSet.of())) {
                final Ticket ticket = t.requiredTicket();
                if (player.has(ticket)) {
                    singleMoves.add(new SingleMove(player.piece(), source, ticket, destination));
                }
            }
            // TODO consider the rules of secret moves here
            //  add moves to the destination via a secret ticket if there are any left with the player
        }
        return ImmutableSet.copyOf(singleMoves);
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
            if(setup.rounds.isEmpty()) throw new IllegalArgumentException("Rounds is empty");
            if(setup.graph.nodes().isEmpty()) throw new IllegalArgumentException("Graph is empty");
            if(mrX == null) throw new NullPointerException("MrX is empty!");
            if(!mrX.piece().webColour().equals("#000")) throw new IllegalArgumentException("MrX must be a black piece");

            checkDetectives(detectives);

            //Build remaining and everyone lists
            ImmutableSet.Builder<Piece> pieceBuilder = ImmutableSet.builder();
            ImmutableList.Builder<Player> everyoneBuilder = ImmutableList.builder();

            pieceBuilder.add(mrX.piece());
            everyoneBuilder.add(mrX);
            for(Player p : detectives){
                pieceBuilder.add(p.piece());
                everyoneBuilder.add(p);
            }

            this.setup = setup;
            this.remaining = pieceBuilder.build();
            this.log = log;
            this.mrX = mrX;
            this.detectives = detectives;
            this.everyone = everyoneBuilder.build();
            this.moves = makeMoves(setup, mrX, detectives);
            this.winner = ImmutableSet.of();
        }

        void checkDetectives(final List<Player> detectives) {
            if(detectives.isEmpty()) throw new IllegalArgumentException("Detectives is empty");

            for(int i = 0; i < detectives.size(); i++){
                Player p = detectives.get(i);
                if (p == null) throw new IllegalArgumentException("One or more detectives are null");
                if(p.isDetective() && p.tickets().get(Ticket.DOUBLE) != 0) throw new IllegalArgumentException("One or more detectives has a double ticket");
                if(p.isDetective() && p.tickets().get(Ticket.SECRET) != 0) throw new IllegalArgumentException("One or more detectives has a secret ticket");
                if(p.isMrX()) throw new IllegalArgumentException("There can only be one MrX");

                for(int j = i + 1; j < detectives.size(); j++){
                    if(p.location() == detectives.get(j).location()) throw new IllegalArgumentException("Two players have the same location");
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
            return remaining;
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

        @Override
        public GameState advance(Move move) {
            // TODO
            return new MyGameState(setup, remaining, log, mrX, detectives);
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