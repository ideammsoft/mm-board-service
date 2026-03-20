package co.kr.mmsoft.mmboardservice.mapper;

import co.kr.mmsoft.mmboardservice.domain.FreeBoard;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FreeBoardMapper {

    /** 목록 조회 (삭제 제외, ref/step 정렬로 댓글 계층 표현) */
    List<FreeBoard> findAll(@Param("rolename") String rolename,
                            @Param("keyword") String keyword);

    /** 단건 조회 */
    FreeBoard findById(@Param("id") Long id);

    /** 조회수 +1 */
    void incrementCnt(@Param("id") Long id);

    /** 글 등록 */
    int insert(FreeBoard board);

    /** ref 최대값 조회 (원글 그룹번호 채번) */
    int maxRef();

    /** 댓글 등록 시: 같은 ref 안에서 step 밀기 */
    void shiftStep(@Param("ref") int ref, @Param("step") int step);

    /** 수정 */
    int update(FreeBoard board);

    /** 소프트 삭제 */
    int delete(@Param("id") Long id);
}
