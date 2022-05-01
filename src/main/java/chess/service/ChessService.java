package chess.service;

import chess.domain.board.Board;
import chess.domain.board.BoardFactory;
import chess.domain.board.PieceFactory;
import chess.domain.board.Position;
import chess.domain.game.ChessGame;
import chess.domain.game.Score;
import chess.domain.game.Turn;
import chess.domain.piece.Piece;
import chess.domain.piece.Team;
import chess.dto.BoardsDto;
import chess.dto.request.RoomAccessRequestDto;
import chess.dto.response.StatusResponseDto;
import chess.dto.request.MoveRequestDto;
import chess.dto.request.RoomRequestDto;
import chess.dto.response.GameResponseDto;
import chess.dto.response.RoomResponseDto;
import chess.dto.response.RoomsResponseDto;
import chess.entity.BoardEntity;
import chess.entity.RoomEntity;
import chess.repository.BoardRepository;
import chess.repository.RoomRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import chess.util.ChessGameAlreadyFinishException;
import chess.util.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@Service
public class ChessService {

    private final RoomRepository roomRepository;
    private final BoardRepository boardRepository;
    private final PasswordEncoder passwordEncoder;

    public ChessService(final RoomRepository roomRepository,
                        final BoardRepository boardRepository,
                        final PasswordEncoder passwordEncoder) {
        this.roomRepository = roomRepository;
        this.boardRepository = boardRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public RoomResponseDto createRoom(final RoomRequestDto roomRequestDto) {
        final RoomEntity room = new RoomEntity(roomRequestDto.getName(),
                passwordEncoder.encode(roomRequestDto.getPassword()),
                Team.WHITE.getValue(),
                false);
        final RoomEntity createdRoom = roomRepository.insert(room);
        boardRepository.batchInsert(createBoards(createdRoom));
        return RoomResponseDto.of(createdRoom);
    }

    private List<BoardEntity> createBoards(final RoomEntity createdRoom) {
        final Map<Position, Piece> board = BoardFactory.initialize();
        return board.entrySet().stream()
            .map(entry -> new BoardEntity(createdRoom.getId(),
                entry.getKey().convertPositionToString(),
                entry.getValue().convertPieceToString()))
            .collect(Collectors.toList());
    }

    public GameResponseDto enterRoom(final Long roomId) {
        final RoomEntity room = roomRepository.findById(roomId);
        validateGameOver(room);
        return GameResponseDto.of(room, boardRepository.findBoardByRoomId(roomId));
    }

    public GameResponseDto move(final Long id, final MoveRequestDto moveRequestDto) {
        final RoomEntity room = roomRepository.findById(id);
        validateGameOver(room);
        final List<BoardEntity> boardEntity = boardRepository.findBoardByRoomId(id);

        final ChessGame chessGame = new ChessGame(toBoard(boardEntity), new Turn(Team.of(room.getTeam())));
        final String sourcePosition = moveRequestDto.getSource();
        final String targetPosition = moveRequestDto.getTarget();

        chessGame.move(sourcePosition, targetPosition);

        final BoardEntity sourceBoardEntity = new BoardEntity(id, sourcePosition,
            chessGame.getPieceName(sourcePosition));
        final BoardEntity targetBoardEntity = new BoardEntity(id, targetPosition,
            chessGame.getPieceName(targetPosition));
        boardRepository.updateBatchPositions(List.of(sourceBoardEntity, targetBoardEntity));

        final String turnAfterMove = chessGame.getCurrentTurn().getValue();
        roomRepository.updateTeam(id, turnAfterMove);
        updateGameOver(id, chessGame);
        return GameResponseDto.of(roomRepository.findById(id), boardRepository.findBoardByRoomId(id));
    }

    private void updateGameOver(final Long id, final ChessGame chessGame) {
        if (!chessGame.isOn()) {
            roomRepository.updateGameOver(id);
        }
    }

    private Board toBoard(final List<BoardEntity> boardEntity) {
        final Map<Position, Piece> board = boardEntity.stream()
            .collect(Collectors.toMap(it -> Position.valueOf(it.getPosition()),
                it -> PieceFactory.createPiece(it.getPiece())));

        return new Board(board);
    }

    @Transactional(readOnly = true)
    public RoomsResponseDto findRooms() {
        final List<RoomEntity> rooms = roomRepository.findRooms();
        return RoomsResponseDto.of(rooms);
    }

    public void endRoom(final Long id, final RoomAccessRequestDto roomAccessRequestDto) {
        final RoomEntity room = roomRepository.findById(id);
        validatePassword(roomAccessRequestDto.getPassword(), room.getPassword());
        validateGameOver(room);
        roomRepository.updateGameOver(id);
    }

    public StatusResponseDto createStatus(final Long id) {
        final Board board = toBoard(boardRepository.findBoardByRoomId(id));
        return StatusResponseDto.of(new Score(board.getBoard()));
    }

    public void updateRoomName(final Long id, final RoomRequestDto roomRequestDto) {
        RoomEntity room = roomRepository.findById(id);
        validatePassword(roomRequestDto.getPassword(), room.getPassword());
        validateGameOver(room);
        roomRepository.updateName(id, roomRequestDto.getName());
    }

    private void validateGameOver(final RoomEntity room) {
        if (room.isGameOver()) {
            throw new ChessGameAlreadyFinishException("[ERROR] 이미 종료된 게임입니다.");
        }
    }

    private void validatePassword(final String password, final String roomPassword) {
        if (!roomPassword.equals(passwordEncoder.encode(password))) {
            throw new IllegalArgumentException("[ERROR] 비밀번호가 틀렸습니다.");
        }
    }
}
