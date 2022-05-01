package chess.controller;

import chess.config.MockMvcConfig;
import chess.domain.board.BoardFactory;
import chess.domain.game.Score;
import chess.dto.request.MoveRequestDto;
import chess.dto.request.RoomAccessRequestDto;
import chess.dto.request.RoomRequestDto;
import chess.dto.response.*;
import chess.entity.BoardEntity;
import chess.entity.RoomEntity;
import chess.service.ChessService;
import chess.util.ChessGameAlreadyFinishException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.stream.Collectors;

import static chess.controller.ControllerTestFixture.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(MockMvcConfig.class)
@ActiveProfiles("test")
@WebMvcTest(ChessController.class)
class ChessControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ChessService chessService;

    @Autowired
    private ObjectMapper objectMapper;

    @DisplayName("방을 생성하면 201 create와 Location을 헤더로 반환한다.")
    @Test
    void create() throws Exception {
        final RoomRequestDto roomRequestDto = new RoomRequestDto(ROOM_NAME, ROOM_PASSWORD);
        String content = objectMapper.writeValueAsString(roomRequestDto);

        given(chessService.createRoom(any(RoomRequestDto.class)))
                .willReturn(RoomResponseDto.of(createRoomEntity(1L)));

        mockMvc.perform(post(DEFAULT_API)
                        .content(content)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, "/api/chess/rooms/1"));
    }

    @DisplayName("진행 중인 모든 방을 조회하고 200 ok 를 반환한다.")
    @Test
    void findRooms() throws Exception {
        RoomsResponseDto roomsResponseDto = RoomsResponseDto.of(List.of(createRoomEntity(1L)
                , createRoomEntity(2L)));
        String response = objectMapper.writeValueAsString(roomsResponseDto);

        given(chessService.findRooms())
                .willReturn(roomsResponseDto);

        mockMvc.perform(get(DEFAULT_API))
                .andExpect(status().isOk())
                .andExpect(content().string(response));
    }

    @DisplayName("진행 중인 방을 들어가면 200 ok와 gameResponseDto를 반환한다.")
    @Test
    void enterRoom() throws Exception {
        GameResponseDto gameResponseDto = GameResponseDto.of(createRoomEntity(1L)
                , createBoardEntities());
        String response = objectMapper.writeValueAsString(gameResponseDto);

        given(chessService.enterRoom(anyLong()))
                .willReturn(gameResponseDto);

        mockMvc.perform(get(DEFAULT_API + "/1"))
                .andExpect(status().isOk())
                .andExpect(content().string(response));
    }

    @DisplayName("종료된 방을 들어가면 409 에러가 발생한다.")
    @Test
    void enterRoomException() throws Exception {
        ErrorResponseDto errorResponseDto = new ErrorResponseDto(ERROR_FINISHED);
        String response = objectMapper.writeValueAsString(errorResponseDto);

        given(chessService.enterRoom(anyLong()))
                .willThrow(new ChessGameAlreadyFinishException(ERROR_FINISHED));

        mockMvc.perform(get(DEFAULT_API + "/1"))
                .andExpect(status().isConflict())
                .andExpect(content().string(response));
    }

    @DisplayName("게임을 종료하면 200 ok와 statusResponseDto를 반환한다.")
    @Test
    void finishGame() throws Exception {
        doNothing().when(chessService)
                .endRoom(anyLong(), any(RoomAccessRequestDto.class));
        StatusResponseDto statusResponseDto = StatusResponseDto.of(new Score(BoardFactory.initialize()));
        String response = objectMapper.writeValueAsString(statusResponseDto);

        given(chessService.createStatus(anyLong()))
                .willReturn(statusResponseDto);

        mockMvc.perform(patch(DEFAULT_API + "/1/end")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RoomAccessRequestDto(ROOM_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(content().string(response));
    }

    @DisplayName("종료된 방을 다시 종료하면 409 에러가 발생한다.")
    @Test
    void finishGameException() throws Exception {
        ErrorResponseDto errorResponseDto = new ErrorResponseDto(ERROR_FINISHED);
        String response = objectMapper.writeValueAsString(errorResponseDto);

        doThrow(new ChessGameAlreadyFinishException(ERROR_FINISHED))
                .when(chessService)
                .endRoom(anyLong(), any(RoomAccessRequestDto.class));

        mockMvc.perform(patch(DEFAULT_API + "/1/end")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RoomAccessRequestDto(ROOM_PASSWORD))))
                .andExpect(status().isConflict())
                .andExpect(content().string(response));
    }

    @DisplayName("room 삭제 시 비밀번호가 틀릴 경우 400 bad request와 errorResponseDto를 반환한다.")
    @Test
    void finishGameNotSamePasswordException() throws Exception {
        RoomRequestDto roomRequestDto = new RoomRequestDto(ROOM_NAME, ROOM_PASSWORD);
        String request = objectMapper.writeValueAsString(roomRequestDto);

        ErrorResponseDto errorResponseDto = new ErrorResponseDto(ERROR_PASSWORD_WRONG);
        String response = objectMapper.writeValueAsString(errorResponseDto);

        doThrow(new IllegalArgumentException(ERROR_PASSWORD_WRONG))
                .when(chessService)
                .endRoom(anyLong(), any(RoomAccessRequestDto.class));

        mockMvc.perform(patch(DEFAULT_API + "/1/end")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request)
                ).andExpect(status().isBadRequest())
                .andExpect(content().string(response));
    }

    @DisplayName("진행 중인 방에서 움직임 요청을 보내면 200 ok와 gameResponseDto를 반환한다.")
    @Test
    void move() throws Exception {
        GameResponseDto gameResponseDto = GameResponseDto.of(createRoomEntity(1L)
                , createBoardEntities());
        String response = objectMapper.writeValueAsString(gameResponseDto);

        MoveRequestDto moveRequestDto = new MoveRequestDto(WHITE_SOURCE, WHITE_TARGET);
        given(chessService.move(anyLong(), any(MoveRequestDto.class)))
                .willReturn(gameResponseDto);

        mockMvc.perform(post(DEFAULT_API + "/1/move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(moveRequestDto))
                ).andExpect(status().isOk())
                .andExpect(content().string(response));
    }

    @DisplayName("움직임 요청시 자신의 차례가 아닌 경우 400 bad request와 errorResponseDto를 반환한다.")
    @Test
    void moveNotMyTurnException() throws Exception {
        ErrorResponseDto errorResponseDto = new ErrorResponseDto(ERROR_NOT_TURN);
        String response = objectMapper.writeValueAsString(errorResponseDto);
        given(chessService.move(anyLong(), any(MoveRequestDto.class)))
                .willThrow(new IllegalStateException(ERROR_NOT_TURN));

        MoveRequestDto moveRequestDto = new MoveRequestDto(BLACK_SOURCE, BLACK_TARGET);
        mockMvc.perform(post(DEFAULT_API + "/1/move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(moveRequestDto))
                ).andExpect(status().isBadRequest())
                .andExpect(content().string(response));
    }

    @DisplayName("움직임 요청시 움직일 수 없는 경우 400 bad request와 errorResponseDto를 반환한다.")
    @Test
    void moveNotMovableException() throws Exception {
        ErrorResponseDto errorResponseDto = new ErrorResponseDto(ERROR_NOT_MOVABLE);
        String response = objectMapper.writeValueAsString(errorResponseDto);
        given(chessService.move(anyLong(), any(MoveRequestDto.class)))
                .willThrow(new IllegalStateException(ERROR_NOT_MOVABLE));

        MoveRequestDto moveRequestDto = new MoveRequestDto(BLACK_SOURCE, WHITE_TARGET);
        mockMvc.perform(post(DEFAULT_API + "/1/move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(moveRequestDto))
                ).andExpect(status().isBadRequest())
                .andExpect(content().string(response));
    }

    @DisplayName("움직임 요청시 게임이 종료된 경우 409 bad request와 errorResponseDto를 반환한다.")
    @Test
    void moveException() throws Exception {
        ErrorResponseDto errorResponseDto = new ErrorResponseDto(ERROR_FINISHED);
        String response = objectMapper.writeValueAsString(errorResponseDto);

        given(chessService.move(anyLong(), any(MoveRequestDto.class)))
                .willThrow(new ChessGameAlreadyFinishException(ERROR_FINISHED));

        MoveRequestDto moveRequestDto = new MoveRequestDto(BLACK_SOURCE, WHITE_TARGET);
        mockMvc.perform(post(DEFAULT_API + "/1/move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(moveRequestDto))
                ).andExpect(status().isConflict())
                .andExpect(content().string(response));
    }


    @DisplayName("score 요청시 200 ok와 scoreResponseDto를 반환한다.")
    @Test
    void createStatus() throws Exception {
        StatusResponseDto statusResponseDto = StatusResponseDto.of(new Score(BoardFactory.initialize()));
        String response = objectMapper.writeValueAsString(statusResponseDto);

        given(chessService.createStatus(anyLong()))
                .willReturn(statusResponseDto);

        mockMvc.perform(get(DEFAULT_API + "/1/status"))
                .andExpect(status().isOk())
                .andExpect(content().string(response));
    }

    @DisplayName("room 이름 변경 시 200 ok를 반환한다.")
    @Test
    void changeRoomName() throws Exception {
        RoomRequestDto roomRequestDto = new RoomRequestDto(ROOM_NAME, ROOM_PASSWORD);
        String request = objectMapper.writeValueAsString(roomRequestDto);

        doNothing().when(chessService).updateRoomName(1L, roomRequestDto);

        mockMvc.perform(patch(DEFAULT_API + "/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request)
        ).andExpect(status().isNoContent());
    }

    @DisplayName("종료된 room 이름 변경 시 409 conflict와 errorResponseDto를 반환한다.")
    @Test
    void changeRoomNameException() throws Exception {
        RoomRequestDto roomRequestDto = new RoomRequestDto(ROOM_NAME, ROOM_PASSWORD);
        String request = objectMapper.writeValueAsString(roomRequestDto);

        ErrorResponseDto errorResponseDto = new ErrorResponseDto(ERROR_FINISHED);
        String response = objectMapper.writeValueAsString(errorResponseDto);

        doThrow(new ChessGameAlreadyFinishException(ERROR_FINISHED))
                .when(chessService)
                .updateRoomName(anyLong(), any(RoomRequestDto.class));

        mockMvc.perform(patch(DEFAULT_API + "/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request)
                ).andExpect(status().isConflict())
                .andExpect(content().string(response));
    }

    @DisplayName("room 이름 변경 시 비밀번호가 틀릴 경우 400 bad request와 errorResponseDto를 반환한다.")
    @Test
    void changeRoomNameNotSamePasswordException() throws Exception {
        RoomRequestDto roomRequestDto = new RoomRequestDto(ROOM_NAME, ROOM_PASSWORD);
        String request = objectMapper.writeValueAsString(roomRequestDto);

        ErrorResponseDto errorResponseDto = new ErrorResponseDto(ERROR_PASSWORD_WRONG);
        String response = objectMapper.writeValueAsString(errorResponseDto);

        doThrow(new IllegalArgumentException(ERROR_PASSWORD_WRONG))
                .when(chessService)
                .updateRoomName(anyLong(), any(RoomRequestDto.class));

        mockMvc.perform(patch(DEFAULT_API + "/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request)
                ).andExpect(status().isBadRequest())
                .andExpect(content().string(response));
    }

    private RoomEntity createRoomEntity(Long id) {
        return new RoomEntity(id, ROOM_NAME, ROOM_PASSWORD, WHITE, FALSE);
    }

    private List<BoardEntity> createBoardEntities() {
        return BoardFactory.initialize()
                .entrySet()
                .stream()
                .map(entry -> new BoardEntity(
                        1L,
                        entry.getKey().convertPositionToString(),
                        entry.getValue().convertPieceToString())
                )
                .collect(Collectors.toList());
    }
}
