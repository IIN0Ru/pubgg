package gg.pub.pubgapi;

/**
 * 닉네임으로 플레이어를 찾지 못했을 때 (PUBG API 가 404 를 돌려줄 때).
 */
public class PlayerNotFoundException extends PubgApiException {

    public PlayerNotFoundException(String nickname) {
        super("'" + nickname + "' 플레이어를 찾지 못했어요. 닉네임은 대소문자까지 정확해야 하고, 플랫폼(스팀/카카오)도 맞는지 확인해 주세요.");
    }
}
