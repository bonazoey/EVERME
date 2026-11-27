package com.example.everme.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.example.everme.entity.Member;

@Mapper 
public interface MemberDao {


    public int insert(Member member);

    public Member selectById(@Param ("email") String email);

}
