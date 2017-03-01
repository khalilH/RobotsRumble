/* ******************************************************
 * Simovies - Eurobot 2015 Robomovies Simulator.
 * Copyright (C) 2014 <Binh-Minh.Bui-Xuan@ens-lyon.org>.
 * GPL version>=3 <http://www.gnu.org/licenses/>.
 * $Id: algorithms/Stage1.java 2014-10-18 buixuan.
 * ******************************************************/
package algorithms;

import robotsimulator.Brain;
import characteristics.Parameters.Direction;
import characteristics.Parameters;
import characteristics.IFrontSensorResult;
import characteristics.IRadarResult;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Random;


public class FightBrain extends Brain {
	// ---PARAMETERS---//
	private static final double HEADINGPRECISION = 0.01;
	private static final double ANGLEPRECISION = 0.01;
	private static final int DISTANCE_TO_LEADER = 400;
	private static final double DOUBLE_PI = 6.28318530;
	// ---VARIABLES---//
	private boolean waitTask, dodgeLeftTask, dodgeRightTask, dodgeTask, moveFrontTask, moveBackTask;
	private double endRepositioningDirection;
	private boolean isMoving;
	private int whoAmI;
	private Point  myCoords;
	private boolean doNotShoot;
	private int nbTurns = 0;
	private boolean shouldMove;
	private static Random rand = new Random(); 
	private Point attackedFriend = null;
	// ---CONSTRUCTORS---//
	public FightBrain() {
		super();
	}
	private static int  lol = 0;
	// ---ABSTRACT-METHODS-IMPLEMENTATION---//
	public void activate() {
		// ODOMETRY CODE
		whoAmI = lol++ % 3;
		switch(whoAmI){
		case 0:
			myCoords = new Point((int)Parameters.teamAMainBot1InitX,(int)Parameters.teamAMainBot1InitY);
			break;
		case 1:
			myCoords = new Point((int)Parameters.teamAMainBot2InitX,(int)Parameters.teamAMainBot2InitY);
			break;
		case 2:
			myCoords = new Point((int)Parameters.teamAMainBot3InitX,(int)Parameters.teamAMainBot3InitY);
			break;
		}
		// INIT
		waitTask = false;
		moveFrontTask = false;
		moveBackTask = false;
		dodgeTask = false;
		dodgeLeftTask = false;
		dodgeRightTask = false;
		isMoving = false;
		shouldMove = false;
	}

	public void step() {
		int enemyFighters, enemyPatrols;
		double enemyDirection;
		ArrayList<IRadarResult> radarResults;
		if (getHealth() <= 0)
			return;
		sendLogMessage("position ("+myCoords.x+", "+(int)myCoords.y+"). Avec un heading De "+getHeading());
		
		/*** Si on est au point de ralliement on stop le rapprochement ***/
		if(isAtRallyPoint(attackedFriend, myCoords)){
			attackedFriend = null;
		}
		
		/*** Permet de reculer lorsque trop rpes ***/
		if(moveBackTask && nbTurns == 0){
			moveBackTask = false;
			dodgeObstacle();
			return;
		}
		if (moveBackTask && nbTurns > 0) {
			MyMoveBack();
			nbTurns--;
	        return;
		}
		
		/*** Permet de reculer lorsque trop rpes ***/
		if(moveFrontTask && nbTurns == 0){
			moveFrontTask = false;
			return;
		}
		if (moveFrontTask && nbTurns > 0) {
			MyMove();
			nbTurns--;
	        return;
		}
		/*** Permet au robot de se positioner vers son NORD ***/
		if (dodgeTask && nbTurns == 0) {
			dodgeTask = false;
			dodgeLeftTask = false;
			dodgeRightTask = false;
			return;
		}
		/***
		 * Tant que le robot n'est pas bien positionne on tourne a droite
		 * jusqu'a atteindre le NORD
		 ***/
		if (dodgeTask && nbTurns > 0) {
			if(dodgeLeftTask)
				stepTurn(Direction.LEFT);
			else
				stepTurn(Direction.RIGHT);
	        nbTurns--;
	        return;
		}


		if (!dodgeTask && !moveBackTask) {
			radarResults = detectRadar();
			enemyFighters = 0;
			enemyPatrols = 0;
			enemyDirection = 0;
			doNotShoot = false;
			for (IRadarResult r : radarResults) {
				/** Focus le Main **/
				if (r.getObjectType() == IRadarResult.Types.OpponentMainBot) {
					enemyFighters++;
					enemyDirection = r.getObjectDirection();
				}
				/** Au cas ou il ya un secondary **/
				if (r.getObjectType() == IRadarResult.Types.OpponentSecondaryBot) {
					if (enemyFighters == 0)
						enemyDirection = r.getObjectDirection();
					enemyPatrols++;
				}
				/** Ne pas tirer sur friends **/
				if (r.getObjectType() == IRadarResult.Types.TeamMainBot
						|| r.getObjectType() == IRadarResult.Types.TeamSecondaryBot) {
					if (isInFrontOfMe(r.getObjectDirection()) && enemyFighters + enemyPatrols == 0) {
						doNotShoot = true;
					}
					
					if (r.getObjectDistance() <= r.getObjectRadius() + Parameters.teamAMainBotRadius + 50 &&  (enemyFighters+enemyPatrols) == 0) {
						dodgeObstacle(r.getObjectDirection(), r.getObjectDistance());
						return;
					}
				}
				
				if(r.getObjectType() == IRadarResult.Types.Wreck ){
					if (r.getObjectDistance() <= r.getObjectRadius() + Parameters.teamAMainBotRadius + 50 &&  (enemyFighters+enemyPatrols) == 0) {
						dodgeObstacle(r.getObjectDirection(), r.getObjectDistance());
						return;
					}
				}
				/** Reculer si trop proche **/
				if(r.getObjectType() == IRadarResult.Types.TeamMainBot || r.getObjectType() == IRadarResult.Types.TeamSecondaryBot || r.getObjectType() == IRadarResult.Types.Wreck){
					if(r.getObjectDistance() <= r.getObjectRadius() + Parameters.teamAMainBotRadius + 20 && !dodgeTask && (enemyFighters+enemyPatrols) == 0){
						moveBackTast(r.getObjectDirection());
						return;
					}
				}
			}

			/*** Comporte de base lorsque dennemi detecte ***/
			if (enemyFighters + enemyPatrols > 0) {
				attackedFriend = null;
				attack(enemyDirection);
				return;
			}
		}
		
		/***
		 * Si le robot n'est pas en mode tourner et qu'il detecte un wall alors
		 * tourne a gauche
		 ***/
		if (detectFront().getObjectType() == IFrontSensorResult.Types.Wreck) {
			for (IRadarResult r : detectRadar()) {
				if(r.getObjectType() == IRadarResult.Types.Wreck && r.getObjectDistance() <= r.getObjectRadius() + Parameters.teamAMainBotRadius + 50){
					dodgeObstacle(r.getObjectDirection(), r.getObjectDistance());
					return;
				}
			}
		}
		
//		/*** Permet de se positioner pour se rapproche du leader ***/
//		if(attackedFriend != null){
//			approximate(attackedFriend);
//			return;
//		}
		
		if (detectFront().getObjectType() == IFrontSensorResult.Types.WALL) {
			dodgeObstacle();
			return;
		}

//		// Ici on essaye de rester close sinon random
		ArrayList<Point> list = new ArrayList<Point>();
		for (String s : fetchAllMessages()) {
			String tab[] = s.split("-");
			if (tab.length < 3 || tab[0].equals(String.valueOf(whoAmI)) || tab[1].equals("") || tab[2].equals(""))
				continue;
			Point leaderCoord = new Point(Integer.parseInt(tab[1]),
					Integer.parseInt(tab[2]));
			list.add(leaderCoord);
		}
		if(list.size() == 1 ){
			attackedFriend = list.get(0);
			approximate(attackedFriend);
			return;
		}
		
		if(list.size() == 2){
			attackedFriend = list.get(0).distance(myCoords) < list.get(1).distance(myCoords) ? list.get(0) : list.get(1);
			approximate(attackedFriend);
			return;
		}
		moveRandom();
		
	}
	private void MyMove(){
		myCoords.setLocation(myCoords.getX() + Parameters.teamAMainBotSpeed * Math.cos(getHeading()), myCoords.getY() + Parameters.teamAMainBotSpeed * Math.sin(getHeading()));
		move();
	}
	private void MyMoveBack(){
		myCoords.setLocation(myCoords.getX() - Parameters.teamAMainBotSpeed * Math.cos(getHeading()), myCoords.getY() - Parameters.teamAMainBotSpeed * Math.sin(getHeading()));
		moveBack();
	}
	private void moveRandom(){
		/*** DEFAULT COMPORTEMENT ***/
		double randDouble = Math.random();
		if(randDouble <= 0.60){
			MyMove();
			return;
		}
		if(randDouble <= 0.80){
			stepTurn(Direction.LEFT);
			return;
		}
		if(randDouble <= 1.00 ){
			stepTurn(Direction.RIGHT);
			return;
		}
	}
	
	private boolean isAtRallyPoint(Point p1, Point p2){
		if(p1 == null || p2 == null)
			return false;
		return p1.distance(p2) < DISTANCE_TO_LEADER;
	}
	private void dodgeObstacle(){
		dodgeTask = true;
		if(Math.random() > 0.5){
			dodgeLeftTask = true;
		}else{
			dodgeRightTask = true;
		}
		nbTurns = rand.nextInt(40);
	}
	private void dodgeObstacle(double pos, double distance){
		dodgeTask = true;
		if(isADroite(pos) && isDevant(pos)){
			dodgeLeftTask = true;
		}else{
			if(isAGauche(pos) && isDevant(pos)){
			dodgeRightTask = true;
			}else{
				if(isDevant(pos)){
					moveBackTask = true;
				}else{
					moveFrontTask = true;
				}
			}
		}
		nbTurns = rand.nextInt(40);
	}
	private void moveBackTast(double pos){
		if(isDerriere(pos)){
			moveFrontTask = true;
		}else{
			moveBackTask = true;
		}
		nbTurns = rand.nextInt(40);
	}

	private void attack(double enemyDirection) {
		shouldMove = !shouldMove;
		broadcast(whoAmI+"-"+myCoords.x+"-"+myCoords.y);
		if(shouldMove){
			if(isDerriere(enemyDirection)){
				MyMove();
				return;
			}else{
				MyMoveBack();
			}
		}
		else if(!doNotShoot){
			fire(enemyDirection);
			return;
		}
	}

	private boolean isInFrontOfMe(Double enemy) {
		double heading = getHeading();
		double left = 0.15 * Math.PI;
		double right = -0.15 * Math.PI;
		boolean res = enemy <= (heading + left) % (DOUBLE_PI) && enemy >= (heading + right) % (DOUBLE_PI);
		return res;
	}	
	private boolean isDevant(double pos){
		double heading = getHeading();
		double left = 0.5 * Math.PI;
		if(heading < 0 )
			heading = (heading + DOUBLE_PI) % (DOUBLE_PI);
		if(pos < 0)
			pos = (pos + DOUBLE_PI) % (DOUBLE_PI);
		
		double leftBorn = (heading + left) % (DOUBLE_PI);
		double rightBorn = (heading - left) % (DOUBLE_PI);
		if(leftBorn < 0)
			leftBorn = (leftBorn + DOUBLE_PI) % (DOUBLE_PI);
		if(rightBorn < 0)
			rightBorn = (rightBorn + DOUBLE_PI) % (DOUBLE_PI);
		if(heading - left > 0 && heading + left < DOUBLE_PI){
			return pos <= leftBorn  && pos >= rightBorn;
		}else{
				return pos >= rightBorn || pos <= leftBorn;
		}
	}
	
	private boolean isDerriere(double pos){
		return !isDevant(pos);
	}
	
	private boolean isAGauche(double pos){
		double heading = getHeading();
		if(heading < 0 )
			heading = heading + DOUBLE_PI;
		if(pos < 0)
			pos = (pos + DOUBLE_PI) % (DOUBLE_PI);
		double left = Math.PI;
		double leftBorn = heading % (DOUBLE_PI); // Heading actuel
		double rightBorn = (heading - left) % (DOUBLE_PI); // Heading - PI

		if(leftBorn < 0)
			leftBorn = (leftBorn + DOUBLE_PI) % (DOUBLE_PI);
		if(rightBorn < 0)
			rightBorn = (rightBorn + DOUBLE_PI) % (DOUBLE_PI);
		
		if(heading - Math.PI > 0){ // Cas dans les bornes
			return pos <= leftBorn  && pos >= rightBorn ;
		}else{
			return pos >= rightBorn || pos <= leftBorn;
		}
				
	}
	
	private boolean isADroite(double pos){
		return !isAGauche(pos);
	}
	
	
	private boolean isHeading(double dir1, double dir2) {
		dir1 = dir1 % DOUBLE_PI;
		if(dir1 < 0)
			dir1 = dir1 + DOUBLE_PI;
		double leres =  Math.abs(dir1 - dir2);
		if(leres >= 6.28)
			leres = 0;
		return leres  < ANGLEPRECISION;
	}

	private void approximate(Point leaderCoord) {
		System.out.println("Je suis en "+myCoords+" et je dois aller en "+leaderCoord);
		if(myCoords.x >= leaderCoord.x - DISTANCE_TO_LEADER  && myCoords.x <= leaderCoord.x + DISTANCE_TO_LEADER){
			if(myCoords.y >= leaderCoord.y - DISTANCE_TO_LEADER && myCoords.y <= leaderCoord.y + DISTANCE_TO_LEADER){
				moveRandom(); // Cas random au cas ou
			}else{//Sinon il faut se rapproche du Y
				if(myCoords.y > leaderCoord.y){
					monter();
					System.out.println("Du coup je monte");
				}else{
					descendre();
					System.out.println("Du coup je descned");
				}
			}
		}else{ // Sinon rapproche du X
			if(myCoords.x > leaderCoord.x ){
				gauche();
				System.out.println("Du coup je gauche");

			}else{
				droite();
				System.out.println("Du coup je droite");

			}
		}
		MyApproximateMove();
	}
	private void MyApproximateMove() {
		
		if(isHeading(getHeading(), endRepositioningDirection)){
			MyMove();
			return;
		}else{
			stepTurn(whereToTurn(endRepositioningDirection));
			return;
		}
	}
	private Direction whereToTurn(double pos){
		double headingInit = getHeading() % (DOUBLE_PI);
		if(headingInit < 0)
			headingInit = headingInit + DOUBLE_PI;
		pos =  pos % (DOUBLE_PI);
		if( pos < 0)
			pos = pos + DOUBLE_PI;
		int leftTurns = 0, rightTurns = 0;
		double heading = headingInit;
		while(!isHeading(heading, pos)){
			rightTurns++;
			heading = (heading + Parameters.teamAMainBotStepTurnAngle) % (DOUBLE_PI);

		}
		heading = headingInit;
		while(!isHeading(heading, pos)){
			leftTurns++;
			heading = (heading - Parameters.teamAMainBotStepTurnAngle) % (DOUBLE_PI);
			if(heading < 0)
				heading = (heading + DOUBLE_PI) % (DOUBLE_PI);
		}
		return rightTurns < leftTurns ? Direction.RIGHT : Direction.LEFT;
	}
	/**** COMMANDE TO MOVE ***/
	private void monter(){
		endRepositioningDirection = Parameters.NORTH + (DOUBLE_PI);
	}
	private void descendre(){
		endRepositioningDirection = Parameters.SOUTH;
	}
	private void gauche(){
		endRepositioningDirection = Parameters.WEST;
	}
	private void droite(){
		endRepositioningDirection = Parameters.EAST;
	}
}