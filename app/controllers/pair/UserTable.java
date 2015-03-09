package controllers.pair;

import java.util.TreeMap;

import models.User;

public class UserTable
{
	User						UInfo;	// user info list from PairTable
	TreeMap<Double, MSData> 	MSList; // match score list
	long						JoinTime;	// the joining pairing-talk time
}
