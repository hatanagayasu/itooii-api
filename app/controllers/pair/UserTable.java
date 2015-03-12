package controllers.pair;

import java.util.concurrent.ConcurrentHashMap;

import models.User;

import org.bson.types.ObjectId;

public class UserTable
{
	User						UInfo;	// user info list
	ConcurrentHashMap<ObjectId, MSData> 	MSList; // match score list
	long						JoinTime;	// the joining pairing-talk time
}
