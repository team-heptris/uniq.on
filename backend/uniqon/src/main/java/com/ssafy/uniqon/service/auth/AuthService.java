package com.ssafy.uniqon.service.auth;

import com.ssafy.uniqon.config.jwt.TokenProvider;
import com.ssafy.uniqon.domain.member.Member;
import com.ssafy.uniqon.dto.member.MemberJoinDto;
import com.ssafy.uniqon.dto.member.MemberLoginDto;
import com.ssafy.uniqon.dto.member.MetaMaskLoginDto;
import com.ssafy.uniqon.dto.token.TokenDto;
import com.ssafy.uniqon.dto.token.TokenRequestDto;
import com.ssafy.uniqon.exception.ex.CustomException;
import com.ssafy.uniqon.repository.member.MemberRepository;
import com.ssafy.uniqon.service.s3.AwsS3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.ssafy.uniqon.exception.ex.ErrorCode.*;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final MemberRepository memberRepository;
    private final AuthenticationManagerBuilder authenticationManagerBuilder;
    private final AuthenticationManager authenticationManager;
    private final TokenProvider tokenProvider;

    private final AwsS3Service awsS3Service;

    @Transactional
    public void signup(MemberJoinDto memberJoinDto){
        if(memberRepository.existsByEmail(memberJoinDto.getEmail())){
            throw new CustomException(ALREADY_SAVED_MEMBER);
        }

        Member member = memberJoinDto.toMember();
        member.changeProfileImage(getRandomImage());
        memberRepository.save(member);
    }

//    @Transactional
//    public TokenDto login(MemberLoginDto memberLoginDto) throws RuntimeException{
//
//        // 1. Login ID/PW 를 기반으로 AuthenticationToken 생성
//        UsernamePasswordAuthenticationToken authenticationToken = memberLoginDto.toAuthentication();
//
//        // 2. 실제로 검증 (사용자 비밀번호 체크) 이 이루어지는 부분
//        //    authenticate 메서드가 실행이 될 때 CustomUserDetailsService 에서 만들었던 loadUserByUsername 메서드가 실행됨
//        Authentication authentication = authenticationManagerBuilder.getObject().authenticate(authenticationToken);
//
//        // 3. 인증 정보를 기반으로 JWT 토큰 생성
//        TokenDto tokenDto = tokenProvider.generateTokenDto(authentication);
//        // 5. 토큰 발급
//        return tokenDto;
//    }

    @Transactional
    public TokenDto metaMasklogin(String walletAddress) throws RuntimeException{

        MetaMaskLoginDto metaMaskLoginDto = new MetaMaskLoginDto(walletAddress, "");

        // 1. Login ID/PW 를 기반으로 AuthenticationToken 생성
        UsernamePasswordAuthenticationToken authenticationToken = metaMaskLoginDto.toAuthentication();

        // AuthenticationManager 에 token 을 넘기면 UserDetailsService 가 받아 처리하도록 한다.
//        Authentication authentication = authenticationManager.authenticate(authenticationToken);

        Authentication authentication = authenticationManagerBuilder.getObject().authenticate(authenticationToken);

        // 3. 인증 정보를 기반으로 JWT 토큰 생성
        TokenDto tokenDto = tokenProvider.generateTokenDto(authentication);
        // 5. 토큰 발급
        return tokenDto;
    }

    @Transactional
    public TokenDto reissue(TokenRequestDto tokenRequestDto) {
        // 1. Refresh Token 검증
        if (!tokenProvider.validateToken(tokenRequestDto.getRefreshToken())) {
            throw new CustomException(INVALID_REFRESH_TOKEN);
        }
        // 2. Access Token 에서 Member ID 가져오기
        Authentication authentication = tokenProvider.getAuthentication(tokenRequestDto.getAccessToken());

        //redis에 있는 refreshtoken 비교
        tokenProvider.checkRefreshToken(authentication.getName(), tokenRequestDto.getRefreshToken());

        // 5. 새로운 토큰 생성
        TokenDto tokenDto = tokenProvider.generateTokenDto(authentication);

        // 토큰 발급
        return tokenDto;
    }

    @Transactional
    public void logout(String accessToken) {
        Authentication authentication = tokenProvider.getAuthentication(accessToken);
        tokenProvider.logout(authentication.getName(), accessToken);
    }

    public String getRandomImage() {
        int random = (int) (Math.random() * 6) + 1;
        String path = "member/default/" + random + ".jpg";
        String thumbnailPath = awsS3Service.getThumbnailPath(path);
        return thumbnailPath;
    }

}
