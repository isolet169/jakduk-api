package com.jakduk.repository;

import com.jakduk.common.CommonConst;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import com.jakduk.model.db.User;
import com.jakduk.model.simple.OAuthProfile;
import com.jakduk.model.simple.SocialUserOnLogin;
import com.jakduk.model.simple.UserOnAuthentication;
import com.jakduk.model.simple.UserOnPasswordUpdate;
import com.jakduk.model.simple.UserProfile;

public interface UserRepository extends MongoRepository<User, String> {
	
	User findById(String id);
	User findByUsername(String username);
	User findOneByUsername(String username);
	User findByEmail(String email);
	User findOneByEmail(String email);
	
	@Query(value="{'id' : ?0}", fields="{'username' : 1}")
	User writerFindById(String id);
	
	@Query(value="{'id' : ?0}")
	UserProfile userProfileFindById(String id);
	
	@Query(value="{'id' : ?0}")
	UserOnPasswordUpdate userOnPasswordUpdateFindById(String id);
	
	@Query(value="{'id' : {$ne : ?0}, 'username' : ?1}", fields="{'id' : 1, 'username' : 1}")
	UserProfile userFindByNEIdAndUsername(String id, String username);

	@Query(value="{'socialInfo.providerId' : ?0, 'socialInfo.oauthId' : ?1}")
	User userFindByOauthUser(CommonConst.ACCOUNT_TYPE providerId, String oauthId);

	@Query(value="{'email' : ?0}")
	SocialUserOnLogin findSocialUserByEmail(String email);

	@Query(value="{'socialInfo.providerId' : ?0, 'socialInfo.oauthId' : ?1}")
	SocialUserOnLogin findByOauthUser(CommonConst.ACCOUNT_TYPE providerId, String oauthId);
	
	@Query(value="{'socialInfo.oauthId' : {$ne : ?0}, 'username' : ?1}", fields="{'id' : 1, 'username' : 1, 'socialInfo' : 1}")
	OAuthProfile userFindByNEOauthIdAndUsername(String oauthId, String username);
	
	@Query(value="{'socialInfo.providerId' : ?0, 'socialInfo.oauthId' : ?1}")
	OAuthProfile userfindByOauthUser(CommonConst.ACCOUNT_TYPE providerId, String oauthId);
	
	@Query(value="{'email' : ?0}")
	UserOnAuthentication userFindByEmail(String email);
}
