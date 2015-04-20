package jBugLife;

import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;

/**
 * A very simple, self contained game that demonstrates some Java 8 features,
 * namely lambdas and streams.
 * 
 * A bug is walking slowly and trying to catch stars. It jumps to collect them
 * when the player hits the space bar. For each non yellow star, it gets 5
 * points. For each yellow star, a random number of multicolored stars is
 * generated. For each yellow star, the bug gets 10 points, for each other
 * color, it gets 5 points.
 * 
 * Note that if you want to write a serious game, it is not advisable to model
 * it after this one. For instance, there a native way in JavaFX to animate, and
 * the timing mechanism used for this game is not precise and causes the
 * animation to be choppy.
 * 
 * This game was originally written to demonstrate rxScala, a reactive streams
 * library. The original version is very interesting in its own right. You can
 * check it out at
 * https://github.com/Applied-Duality/RxGame/tree/master/src/aBugsLife
 * 
 * @author Nosheen Zaza
 *
 */
public class Game extends javafx.application.Application { 
	/* Game parameters */
	static final String RES_DIR = "file:///" + System.getProperty("user.dir") + "/resources";
	static final int SCREEN_WIDTH = 800;
	static final int SCREEN_HEIGHT = 600;
	static final int REFRESH_RATE = 30;
	static final int GRASS_SPEED = 1;  // 1px / 30ms
	static final int SUN_SPEED = 8;
	static final int JUMP_INIT_SPEED = 16;
	static final double GRAVITY = 0.4;
	
	/**
	 * Accumulator for collected points. one of only 3 mutable (non final)
	 * variables declared in this game.
	 */
	private int points = 0;
	
	public static void main(final String[] args) {
		javafx.application.Application.launch(args);
	}
	
	@Override
	public void start(final Stage stage) {
		/* Initialize the window and containers */
		final StackPane root = new StackPane();
		root.setAlignment(Pos.BOTTOM_LEFT);
		
		/* Setup the sky */
	    final Canvas sky = new Canvas(SCREEN_WIDTH, SCREEN_HEIGHT);
		final GraphicsContext skyContext = sky.getGraphicsContext2D();
		skyContext.setFill(Color.AZURE);
		skyContext.fillRect(0, 0, SCREEN_WIDTH,SCREEN_HEIGHT);
	    root.getChildren().add(sky);
	    
	    /* Setup game entities entities */ 
	    final Collectible collectible = new Collectible(root);
	    final Bug bug = new Bug(root);
	    final Grass grass = new Grass(root);
	    
	    /* Create a stream */
	    List<Entity> entities = Stream.of(grass, collectible, bug)
				.collect(Collectors.toList());
	    
	    /* Text for points collected */
	    final Text pointsText = new Text (0, 0, "");
	    pointsText.setFont(Font.font ("Verdana", 24));
	    pointsText.setTranslateX(SCREEN_WIDTH - 80);
	    pointsText.setTranslateY(-(SCREEN_HEIGHT - 50));
	    root.getChildren().add(pointsText);
	    
	    /* Game loop */ 
	    /*
	     * We pass the game logic as a lambda parameter to a
	     * method that will execute it periodically. 
	     */
		gameLoop(() -> {
			/* Lambda expression passed, it implements the logic of method run() 
			 * of functional interface Runnable.  
			 */
			Platform.runLater(() -> {
				entities.forEach(e -> e.update());
				pointsText.setText("" + points);
			});
			checkCollision(collectible, bug, root);
		});	    	
	    
	    final Scene scene = new Scene(root);
	    /* Event handler passing using a lambda expression */
	    scene.addEventHandler(KeyEvent.KEY_PRESSED,
	    		event -> {if (event.getCode() == KeyCode.SPACE) bug.jump();});
	    
	    /* Setup the game window. */
	    stage.setTitle("A Bug's Life");
	    stage.setScene(scene);
	    stage.setOnCloseRequest(x -> {Platform.exit(); System.exit(0);});
	    stage.show();
	}

	
	private void checkCollision(final Collectible sun, final Bug bug,
			final StackPane root) {
		/*
		 * If a collision has occurred and the bug is still jumping, do
		 * not check for the collision.
		 */
		if (bug.collided)
			return;
		final Bounds sunScene = sun.localToScene(sun.getLayoutBounds());
		final Bounds bugScene = bug.localToScene(sun.getLayoutBounds());
		if (bugScene.intersects(sunScene)) {
			bug.collided = true;
			Platform.runLater(() -> sun.showHeart(true));
			points += 5;
			if (sun.getEffect() == null) {
				final Bonus b = new Bonus(root);
				points += 5 + b.getBonusPoints();
			}
		}
	}
	
	/**
	 * Execute the game logic every REFRESH_RATE milliseconds. 
	 * @param gameLogic Lambda expression of the game logic.
	 */
	private void gameLoop(final FuncVoidVoid gameLogic) {
		new Thread( () -> {
	    	while(true) {
				try {
					TimeUnit.MILLISECONDS.sleep(REFRESH_RATE);
				} catch (Exception e) {e.printStackTrace();}
				/* Lambda passing here */
				gameLogic.run();
	    	}
		}).start();
	}
	
	/**
	 * Use of optional to indicate that this method either returns some color or
	 * no color.
	 * 
	 * @return
	 */
	private static Optional<Color> nextColor() {
		final Color[] colors = new Color[]{Color.BLUE, Color.YELLOWGREEN};
		final Random r = new Random();
		final int index = r.nextInt(3);
		if(index == 2) return Optional.empty();
		else return Optional.of(colors[index]);
	}

	private static interface Entity {
		public void update();
	}
	
	@FunctionalInterface
	private static interface FuncVoidVoid {
		public void run();
	}

	private static class Grass implements Entity{
		private static final Image tile = new Image(RES_DIR + "/GrassBlock.png");
	    private static final double height = tile.getHeight();
	    private final int nrTiles = (int)Math.ceil(SCREEN_WIDTH/tile.getWidth()) + 1;
	    private final List<ImageView> tiles;   
	    public Grass(final StackPane root) {
			/*
			 * We change the stream to a list so we can repeatedly operate on
			 * the tiles. Note that the tiles themselves are still mutable.
			 * 
			 * The use of constructing tiles like this is that we can
			 * parallelize tile creation just by modifying to .range(0,
			 * nrTiles).parallel() Note that, if we added each tile directly to
			 * root using root.getChildren().add(this) when we use parallel(),
			 * we get an exception, this is because collection storing children
			 * in root is not thread-safe. This is one example that shows why
			 * you need to be very careful when you mutate variables outside the
			 * lambda expression, and why you should always strive to minimize
			 * mutability.
			 */
	    	tiles = IntStream
	        		.range(0, nrTiles)
	        		.mapToObj( i -> new ImageView(){ 
	        			{ setImage(tile); 
	        			  setTranslateX(i*getImage().getWidth());
	        			  }
	        			}).collect(Collectors.toList());
	    	
	    	root.getChildren().addAll(tiles);
	    }
	    
	    
		/**
		 * This is what makes the grass move. 
		 */
	    @Override
	    public void update() {
	    	/* Using the new forEach method, which was added to Iterable interface. */
	    	tiles.forEach(tile -> {
				tile.setTranslateX(
					tile.getTranslateX() < -(tile.getImage().getWidth())?
							SCREEN_WIDTH-GRASS_SPEED:
							tile.getTranslateX()-GRASS_SPEED);
			} );
		}
	}
	
	static class Bonus {
		final Image image = new Image(RES_DIR + "/Star.png");
		class BImage extends ImageView{}
		final List <BImage> bonus;
		
		public Bonus(final StackPane root) {
			final Random r = new Random();
			bonus = IntStream.range(0, r.nextInt(7) + 1)
					.mapToObj(i -> new BImage() {
						{
							setImage(image);
							setTranslateX(i * getImage().getWidth());
							setTranslateY(-(SCREEN_HEIGHT - 100));
							setFitWidth(image.getWidth() - 20);
							setFitHeight(image.getHeight() - 40);
							/*
							 * Here you see how we operate on an optional type,
							 * to extract a value. Note that since the absence
							 * of effect is expressed by having a null effect,
							 * we are forced to assign a null eventually to
							 * ColorAdjust. The Java API still does not employ
							 * optional to indicate the absence of value, which
							 * is unfortunate.
							 */
							ColorAdjust color = nextColor().map(
									x -> new ColorAdjust(x.getHue() / 170, 0.3,
											0, 0)).orElse(null);
							this.setEffect(color);
						}
					}).collect(Collectors.toList());

			Platform.runLater(() -> root.getChildren().addAll(bonus));
			
		}
		
		public int getBonusPoints() {
			/*
			 * Use of map and reduce to calculate the points collected. Note how
			 * by using reduce, we avoid the need of having an explicit variable
			 * in our code to accumulate the points. Often times, using stream
			 * operations helps to reduce mutability in code.
			 */
			return bonus.stream()
					.map(x -> x.getEffect() == null?5:10)
					.reduce(0, (x,y) -> x+y);
		}
	}

	static class Collectible extends ImageView implements Entity{
		public void showHeart(boolean b) {
			setImage(b? new Image(RES_DIR + "/Heart.png") : new Image(RES_DIR + "/Star.png"));
		}
		
		public Collectible(StackPane root) {
			showHeart(false);
			setTranslateY(-(SCREEN_HEIGHT - 200));
			root.getChildren().add(this);
		}
		
		public void update() {
			double distance = SUN_SPEED;
			if(getTranslateX() <= -(getImage().getWidth())) {
				ColorAdjust color = nextColor()
						.map(x -> new ColorAdjust(x.getHue() / 170, 0.3, 0, 0))
						.orElse(null);
				
				this.setEffect(color);
				setTranslateX(SCREEN_WIDTH-distance);
				showHeart(false);
			}
			else {
				setTranslateX(getTranslateX()-distance);
			}
		}
	}

	static class Bug extends ImageView implements Entity {
		private final double homeY = (-Grass.height/2)-5;
		/* An iterator over an infinite stream. */
		private final Iterator<Double> jumpPosition;
		private boolean isJumping = false; // Another mutable variable.
		private boolean collided = false;  // Last of 3 mutable variables we needed. 
		final StackPane root;
		
		public Bug(final StackPane root) {
			setImage(new Image(RES_DIR + "/Bug.png"));
	        setTranslateY(homeY);
	        setTranslateX(SCREEN_HEIGHT/2);
	        this.root = root;
	        
	        /*
	         * An infinite stream used to generate jump positions on demand. 
	         */
			jumpPosition = Stream
					.iterate(
							(double) JUMP_INIT_SPEED,
							x -> (getTranslateY() == homeY) ? JUMP_INIT_SPEED
									: x - GRAVITY).map(speed -> {
						double dY = ((1.0 * speed));
						if (getTranslateY() < homeY + dY)
							return getTranslateY() - dY;
						else
							return homeY;
					}).iterator();

			root.getChildren().add(this);
		}
		
		public void jump() {
			isJumping = true;
		}
		
		@Override
		public void update() {
			if(isJumping) {
				double position = jumpPosition.next();
				setTranslateY(position);
				if(position == homeY) {
					isJumping = false;	
					collided = false;
					/*
					 * When the bug hits the ground again after a jump, we
					 * remove the bonus images from teh scene. We use filter
					 * method of streams to do so.
					 */
					List<Node> remove = root.getChildren().stream()
							.filter(x -> x instanceof Bonus.BImage)
							.collect(Collectors.toList());
					root.getChildren().removeAll(remove);
					
				}
			}			
		}
	} 
}	