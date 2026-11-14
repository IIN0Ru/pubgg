import { useState } from 'react';
import { useNavigate } from 'react-router';
import type { Shard } from '../../api/types';
import { searchPath } from '../../lib/search';

/**
 * 검색창 공통: 닉네임 앞뒤 빈칸을 지우고 전적 화면으로 넘어간다.
 * 서버로 폼을 보내지 않고 화면 안에서 주소만 바꾼다 (전적은 전적 화면이 /api 로 불러온다).
 */
export function useSearchSubmit(initialShard: Shard, initialNickname: string) {
  const navigate = useNavigate();
  const [shard, setShard] = useState<Shard>(initialShard);
  const [nickname, setNickname] = useState(initialNickname);
  const [error, setError] = useState<string | null>(null);

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    const name = nickname.trim();
    if (name === '') {
      setError('닉네임을 입력해 주세요.');
      return;
    }
    setError(null);
    void navigate(searchPath(shard, name));
  };

  return { shard, setShard, nickname, setNickname, error, submit };
}
