package skills.stress.users

import groovy.time.TimeCategory
import groovy.transform.ToString
import groovy.util.logging.Slf4j

@Slf4j
class FileBasedUserIdFactory implements UserIdFactory {

    final List<String> userIds

    @ToString(includeNames = true)
    class UserWithExpiration {
        String userId
        Date expireOn

        boolean isExpired() {
            return new Date().after(expireOn)
        }
    }
    final List<UserWithExpiration> currentActiveUsers = Collections.synchronizedList([])

    FileBasedUserIdFactory(String path) {
        File f = new File(path)
        if (!f.exists()) {
            throw new IllegalArgumentException("[{}] does not exist", path)
        }
        List<String> userIdsTmp = []
        f.eachLine {
            userIdsTmp.add(it)
        }
        userIds = Collections.unmodifiableList(userIdsTmp)
        log.info("Loaded [{}] users", userIds.size())

        int numCurrentActiveUsers = Math.min(5000, (userIds.size() / 2).toInteger())
        log.info("[{}] active users users", numCurrentActiveUsers)
        (0..numCurrentActiveUsers - 1).each {
            String userId = userIds[it]
            Date expirationDate = getRandomUserExpiration()
            currentActiveUsers.add(new UserWithExpiration(userId: userId, expireOn: expirationDate))
        }
        log.info("Loaded [{}] active users", currentActiveUsers.size())

    }

    private synchronized UserWithExpiration getNonActiveUserWithExpiration() {
        List<String> currentActiveIds = currentActiveUsers.collect {it.userId}
        List<String> availableToBeActive = userIds.findAll { !currentActiveIds.contains(it) }
        int ranNum = r.nextInt(availableToBeActive.size())
        String newId = availableToBeActive.get(ranNum)
        Date expirationDate = getRandomUserExpiration()
        return new UserWithExpiration(userId: newId, expireOn: expirationDate)
    }

    private Date getRandomUserExpiration() {
        int expirationIMinutes = r.nextInt(25) + 5
        Date expirationDate
        use(TimeCategory) {
            expirationDate = new Date() + expirationIMinutes.minutes
        }
        expirationDate
    }

    Random r = new Random()

    synchronized String getUserId() {
        int ranNum = r.nextInt(currentActiveUsers.size())
        UserWithExpiration userWithExpiration = currentActiveUsers.get(ranNum)
        if (userWithExpiration.isExpired()) {
            UserWithExpiration newActiveUser = getNonActiveUserWithExpiration()
            currentActiveUsers.remove(userWithExpiration)
            currentActiveUsers.add(newActiveUser)
            userWithExpiration = newActiveUser
            log.info("New Active user [{}]", newActiveUser)
        }

        return userWithExpiration.userId
    }

    String getUserByProjectIndex(Integer index) {
        // project index starts at 1
        assert index <= userIds.size()
        String userId = userIds.get(index - 1)
        return userId
    }


    int numUsers() {
        return userIds.size()
    }
}
