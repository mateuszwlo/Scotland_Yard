package uk.ac.bris.cs.scotlandyard.model;

import com.google.common.collect.ImmutableList;

import javax.annotation.Nonnull;

import com.google.common.collect.ImmutableSet;
import org.checkerframework.checker.units.qual.A;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYard.Factory;

import java.util.ArrayList;

/**
 * cw-model
 * Stage 2: Complete this class
 */
public final class MyModelFactory implements Factory<Model> {

	@Nonnull @Override public Model build(GameSetup setup,
	                                      Player mrX,
	                                      ImmutableList<Player> detectives) {
		return new Model() {
			private Board.GameState gameState = new MyGameStateFactory().build(setup, mrX, detectives);
			private ImmutableSet<Observer> observers = ImmutableSet.of();

			@Nonnull
			@Override
			public Board getCurrentBoard() {
				return gameState;
			}

			@Override
			public void registerObserver(@Nonnull Observer observer) {
				final ArrayList<Observer> newObservers = new ArrayList<>(observers);
				if (!newObservers.contains(observer)) newObservers.add(observer);
				else throw new IllegalArgumentException();
				observers = ImmutableSet.copyOf(newObservers);
			}

			@Override
			public void unregisterObserver(@Nonnull Observer observer) {
				if (observer == null) throw new NullPointerException();
				final ArrayList<Observer> newObservers = new ArrayList<>(observers);
				if (newObservers.contains(observer)) newObservers.remove(observer);
				else throw new IllegalArgumentException();
				observers = ImmutableSet.copyOf(newObservers);
			}

			@Nonnull
			@Override
			public ImmutableSet<Observer> getObservers() {
				return observers;
			}

			private void notifyObservers(Observer.Event event) {
				for (Observer o: observers) o.onModelChanged(gameState, event);
			}

			@Override
			public void chooseMove(@Nonnull Move move) {
				gameState = gameState.advance(move);
				notifyObservers(Observer.Event.MOVE_MADE);

				if (!gameState.getWinner().isEmpty()) notifyObservers(Observer.Event.GAME_OVER);
			}
		};
	}
}
